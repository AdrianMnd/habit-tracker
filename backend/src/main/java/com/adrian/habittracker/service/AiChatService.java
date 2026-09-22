package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.HabitResponse;
import com.adrian.habittracker.exception.AiServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el prompt para el chat de recomendacion de habitos y habla con
 * la API de Gemini en modo streaming (endpoint streamGenerateContent).
 * <p>
 * El modelo responde en texto plano (lo que permite mostrarlo token a token
 * segun llega) y, si tiene sugerencias de habitos, las añade al final tras
 * un delimitador. El backend se limita a reenviar el texto en crudo tal
 * cual llega de Gemini; el parseo del delimitador y del JSON de sugerencias
 * ocurre en el frontend una vez el stream termina.
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** Debe coincidir EXACTAMENTE con HABIT_LINE_PREFIX en useHabitChat.ts (frontend). */
    public static final String HABIT_LINE_PREFIX = "HABITO::";

    private static final String SYSTEM_INSTRUCTION = """
            Eres el asistente de habitos dentro de la aplicacion Habit Tracker.

            Tu funcion, segun lo que te escriba el usuario:
            1. Si menciona un objetivo (dormir mejor, ser mas productivo, hacer mas
               ejercicio, reducir el estres, etc.), sugiere entre 3 y 5 habitos
               concretos y accionables que le ayuden a conseguirlo.
            2. Si pregunta o comenta sobre los habitos que ya tiene registrados
               (te los paso como contexto), coméntalos y sugiere ajustes o mejoras
               si procede.
            3. Si el mensaje no encaja en ninguno de los dos casos, responde de
               forma util y breve, sin forzar sugerencias.

            Reglas de contenido:
            - Responde siempre en español, con un tono cercano, breve y motivador
              (evita parrafos largos).
            - Cada habito sugerido debe ser especifico y realizable en un solo dia
              (evita objetivos vagos como "ser mas saludable").
            - No repitas habitos que el usuario ya tiene registrados.

            Formato de tu respuesta (IMPORTANTE, siguelo al pie de la letra):
            1. Escribe tu respuesta conversacional en texto plano, sin JSON ni
               markdown ni listas con guiones o asteriscos.
            2. Si tienes habitos que sugerir, añade cada uno en una linea NUEVA,
               al final de tu respuesta, con este formato EXACTO, sin nada mas
               delante (ni guion, ni numero, ni espacio):
               %s <nombre corto> :: <descripcion breve>
               Ejemplo:
               %s Beber un vaso de agua :: Nada mas levantarte, antes del cafe
               %s Reducir pantallas en comidas :: Deja el movil fuera de la mesa
            3. Si no tienes ninguna sugerencia concreta que aportar, no escribas
               ninguna linea con ese formato; no la rellenes por rellenar.
            """.formatted(HABIT_LINE_PREFIX, HABIT_LINE_PREFIX, HABIT_LINE_PREFIX);

    private final WebClient geminiWebClient;
    private final HabitService habitService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String model;

    /**
     * Devuelve un flujo de fragmentos de texto segun los va generando Gemini.
     * El controlador se limita a exponer este Flux como Server-Sent Events.
     */
    public Flux<String> streamChat(String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new AiServiceException(
                    "La clave de la API de Gemini no esta configurada (GEMINI_API_KEY)"));
        }

        String habitsContext = buildHabitsContext();
        Map<String, Object> requestBody = buildRequestBody(habitsContext, userMessage);

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .queryParam("key", apiKey)
                        .build(model))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .map(this::extractDeltaText)
                .filter(text -> !text.isEmpty())
                .timeout(Duration.ofSeconds(30))
                .onErrorMap(e -> !(e instanceof AiServiceException),
                        e -> new AiServiceException("Fallo el streaming con Gemini", e));
    }

    private String buildHabitsContext() {
        List<HabitResponse> habits = habitService.findAll();
        if (habits.isEmpty()) {
            return "El usuario todavia no tiene ningun habito registrado.";
        }
        String names = habits.stream().map(HabitResponse::name).reduce((a, b) -> a + ", " + b).orElse("");
        return "Habitos que el usuario ya tiene registrados: " + names + ".";
    }

    private Map<String, Object> buildRequestBody(String habitsContext, String userMessage) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
        );

        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", habitsContext + "\n\nMensaje del usuario: " + userMessage))
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction);
        body.put("contents", List.of(userContent));
        // Temperatura baja: menos "creatividad", mas consistencia siguiendo
        // el formato de lineas HABITO:: que necesitamos parsear despues.
        body.put("generationConfig", Map.of("temperature", 0.4));
        return body;
    }

    /**
     * Cada evento SSE de Gemini trae un fragmento de la respuesta en
     * candidates[0].content.parts[0].text. Si el fragmento no trae texto
     * (p. ej. un evento de metadatos), devolvemos cadena vacia y el Flux lo
     * descarta con el filter().
     */
    private String extractDeltaText(String eventDataJson) {
        try {
            JsonNode root = objectMapper.readTree(eventDataJson);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            return textNode.isMissingNode() ? "" : textNode.asText();
        } catch (Exception e) {
            return "";
        }
    }
}
