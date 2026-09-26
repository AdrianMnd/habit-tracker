package com.adrian.habittracker.service;

import com.adrian.habittracker.exception.AiServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Chat con Gemini con function calling, en streaming.
 *
 * El bucle del agente (patron ReAct, como en Task Agent):
 *   1. Se envia la conversacion + el catalogo de herramientas.
 *   2. Si el modelo responde con texto, se reenvia al navegador segun llega.
 *   3. Si responde con uno o varios functionCall, se ejecutan, se añade a la
 *      conversacion lo que pidio el modelo + el resultado (functionResponse),
 *      y se vuelve al paso 1.
 *   4. Termina cuando el modelo responde sin pedir ninguna herramienta.
 *
 * Hacia el navegador se emiten eventos SSE con nombre:
 *   text           -> fragmento de texto (cadena JSON)
 *   suggestions    -> tarjetas de habitos sugeridos (array JSON)
 *   habits_changed -> se ha creado/archivado un habito, hay que refrescar
 *   error          -> {"message": "..."}; el stream termina limpio despues
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** Tope de vueltas del bucle: evita que un modelo "atascado" llame herramientas para siempre. */
    static final int MAX_TOOL_ITERATIONS = 5;

    private static final String SYSTEM_INSTRUCTION = """
            Eres el asistente de habitos dentro de la aplicacion Habit Tracker.
            Ayudas al usuario a definir habitos concretos para sus objetivos, a
            revisar su progreso y a gestionar sus habitos.

            Herramientas:
            - list_habits: consultala antes de recomendar (para no repetir habitos
              que ya tiene), cuando pregunte por su progreso, o para conocer el id
              de un habito.
            - suggest_habits: para RECOMENDAR habitos usala siempre (entre 3 y 5,
              concretos y realizables en un dia), en lugar de escribirlos en el
              texto. El usuario decidira cuales añadir.
            - create_habit y archive_habit: SOLO cuando el usuario pida
              explicitamente crear o archivar algo. Si no esta claro a que habito
              se refiere, preguntale antes de actuar.
            Despues de usar una herramienta, cuenta en una frase lo que has hecho.

            Estilo: responde siempre en español, cercano, breve y motivador, en
            texto plano (sin markdown ni listas con guiones o asteriscos).
            """;

    private final WebClient geminiWebClient;
    private final HabitChatTools tools;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String model;

    public Flux<ServerSentEvent<String>> streamChat(Long userId, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.just(errorEvent("La clave de la API de Gemini no esta configurada (GEMINI_API_KEY)"));
        }

        List<Object> contents = List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userMessage)))
        );

        return runTurn(userId, contents, 0)
                // onErrorResume: cualquier error de todo el bucle (timeout,
                // 4xx/5xx de Gemini, tope de iteraciones...) se convierte en
                // un evento "error" legible y el stream TERMINA LIMPIO. Sin
                // esto, el error cortaba la conexion a medias y el navegador
                // solo veia un "network error" sin explicacion.
                .onErrorResume(e -> {
                    log.warn("Fallo en el chat con Gemini", e);
                    return Flux.just(errorEvent(userFacingMessage(e)));
                });
    }

    /**
     * Una vuelta del bucle: una llamada a Gemini + (si pide herramientas)
     * su ejecucion y la siguiente vuelta, de forma recursiva.
     */
    private Flux<ServerSentEvent<String>> runTurn(Long userId, List<Object> contents, int iteration) {
        if (iteration >= MAX_TOOL_ITERATIONS) {
            return Flux.error(new AiServiceException(
                    "La IA encadeno demasiadas herramientas sin llegar a responder"));
        }

        // Flux.defer: el estado de la vuelta (modelParts) se crea al
        // SUSCRIBIRSE, no al montar el pipeline. Si alguien se suscribiera
        // dos veces, cada suscripcion tendria su propia lista en vez de
        // compartir (y mezclar) una sola.
        return Flux.defer(() -> {
            List<JsonNode> modelParts = new ArrayList<>();

            Flux<ServerSentEvent<String>> textEvents = callGemini(contents)
                    // Cada chunk trae candidates[0].content.parts: un array
                    // de "partes" (texto o functionCall). JsonNode es
                    // Iterable, asi que flatMapIterable las emite una a una.
                    .flatMapIterable(chunk -> chunk.path("candidates").path(0).path("content").path("parts"))
                    // Guardamos TODAS las partes tal cual llegan: si hay
                    // functionCall, habra que devolverle a Gemini su propio
                    // turno intacto (incluidas las "thoughtSignature" que
                    // añaden los modelos con razonamiento; si se pierden,
                    // la API rechaza la siguiente peticion).
                    .doOnNext(modelParts::add)
                    .filter(part -> part.hasNonNull("text") && !part.path("thought").asBoolean(false))
                    .map(part -> part.get("text").asText())
                    .filter(text -> !text.isEmpty())
                    .map(text -> event("text", text));

            // Flux.concat: primero se emite TODO el texto de esta vuelta y,
            // solo cuando termina, se evalua (defer) si hay herramientas que
            // ejecutar - para entonces modelParts ya esta completo.
            return Flux.concat(textEvents, Flux.defer(() -> handleToolCalls(userId, contents, modelParts, iteration)));
        });
    }

    private Flux<ServerSentEvent<String>> handleToolCalls(Long userId, List<Object> contents,
                                                          List<JsonNode> modelParts, int iteration) {
        List<JsonNode> calls = modelParts.stream()
                .filter(part -> part.has("functionCall"))
                .map(part -> part.get("functionCall"))
                .toList();

        if (calls.isEmpty()) {
            return Flux.empty(); // respuesta final: fin del bucle
        }

        // Las herramientas usan JPA, que es BLOQUEANTE. Este codigo corre en
        // un hilo del event loop de Netty (el que recibe la respuesta de
        // Gemini), y bloquear ahi congelaria todas las demas conexiones que
        // ese hilo atiende. subscribeOn(boundedElastic) mueve la ejecucion a
        // un pool de hilos pensado precisamente para trabajo bloqueante.
        return Mono.fromCallable(() -> calls.stream().map(call -> executeCall(userId, call)).toList())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(executions -> {
                    List<Object> nextContents = new ArrayList<>(contents);
                    nextContents.add(Map.of("role", "model", "parts", modelParts));
                    nextContents.add(Map.of("role", "user", "parts",
                            executions.stream().map(ToolExecution::functionResponsePart).toList()));

                    Flux<ServerSentEvent<String>> toolEvents = Flux.fromIterable(executions)
                            .mapNotNull(ToolExecution::event);

                    return Flux.concat(toolEvents, runTurn(userId, nextContents, iteration + 1));
                });
    }

    private record ToolExecution(Map<String, Object> functionResponsePart, ServerSentEvent<String> event) {
    }

    private ToolExecution executeCall(Long userId, JsonNode call) {
        String name = call.path("name").asText();
        log.info("Gemini invoca la herramienta {} para el usuario {}", name, userId);

        HabitChatTools.ToolResult result = tools.execute(userId, name, call.path("args"));

        Map<String, Object> functionResponse = new LinkedHashMap<>();
        // Los modelos recientes numeran cada llamada: si llega un id, hay
        // que devolverlo para que el modelo sepa a que llamada corresponde
        // cada resultado cuando pide varias a la vez.
        if (call.hasNonNull("id")) {
            functionResponse.put("id", call.get("id").asText());
        }
        functionResponse.put("name", name);
        functionResponse.put("response", result.response());

        ServerSentEvent<String> event = result.eventName() == null
                ? null
                : event(result.eventName(), result.eventPayload());
        return new ToolExecution(Map.of("functionResponse", functionResponse), event);
    }

    private Flux<JsonNode> callGemini(List<Object> contents) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))));
        body.put("contents", contents);
        body.put("tools", List.of(Map.of("functionDeclarations", tools.declarations())));
        body.put("generationConfig", Map.of("temperature", 0.4));

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .queryParam("key", apiKey)
                        .build(model))
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .map(this::parseJson)
                // retryWhen: vuelve a SUSCRIBIRSE (= repetir la peticion HTTP)
                // ante errores transitorios: 429 (limite de peticiones del
                // plan gratuito) y 5xx. Backoff exponencial: ~1s y ~2s entre
                // intentos, con algo de "jitter" aleatorio para que muchos
                // clientes no reintenten todos en el mismo milisegundo.
                //
                // Es seguro reintentar aqui porque esos errores llegan con el
                // CODIGO DE ESTADO, antes de que se haya emitido ningun trozo
                // de texto: el usuario nunca vera un fragmento repetido. Un
                // 400 (peticion mal formada) no se reintenta: fallaria igual.
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(AiChatService::isTransient)
                        // Sin esto, al agotar los reintentos Reactor lanza su
                        // propia excepcion "Retries exhausted" y se pierde el
                        // error original (el 429 o el 503 que queremos ver).
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                // timeout(): maximo entre dos elementos consecutivos (y hasta
                // el primero). Si Gemini se queda colgado, cortamos.
                .timeout(Duration.ofSeconds(30));
    }

    static boolean isTransient(Throwable e) {
        return e instanceof WebClientResponseException response
                && (response.getStatusCode().value() == 429 || response.getStatusCode().is5xxServerError());
    }

    private String userFacingMessage(Throwable e) {
        if (e instanceof AiServiceException) {
            return e.getMessage();
        }
        if (e instanceof TimeoutException) {
            return "La IA ha tardado demasiado en responder. Prueba de nuevo.";
        }
        if (e instanceof WebClientResponseException response && response.getStatusCode().value() == 429) {
            return "La IA esta recibiendo demasiadas peticiones. Espera unos segundos y prueba de nuevo.";
        }
        return "No se pudo contactar con la IA. Prueba de nuevo en un momento.";
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AiServiceException("Respuesta de Gemini con formato inesperado", e);
        }
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return event("error", Map.of("message", message));
    }

    /**
     * El data de cada evento va SIEMPRE codificado como JSON (tambien el
     * texto: "hola" viaja como "\"hola\""), igual que antes - el frontend
     * hace JSON.parse() y no depende de como SSE trata espacios y saltos de
     * linea.
     */
    private ServerSentEvent<String> event(String name, Object payload) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(name)
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            throw new AiServiceException("No se pudo serializar un evento del chat", e);
        }
    }
}