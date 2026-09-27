package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.HabitSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba el bucle del agente SIN llamar a Gemini de verdad. En vez de
 * mockear WebClient (una API "fluida" de muchas llamadas encadenadas, muy
 * incomoda de mockear), le damos un ExchangeFunction propio: la pieza de
 * WebClient que de verdad hace la peticion HTTP. Asi todo lo demas
 * (retrieve(), decodificar el SSE, retryWhen, timeout...) es codigo real, y
 * solo la "red" es falsa: cada llamada devuelve la siguiente respuesta de
 * una cola que prepara cada test.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    private static final Long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Deque<ClientResponse> geminiResponses = new ArrayDeque<>();
    private int geminiCalls = 0;

    @Mock
    private HabitChatTools tools;

    @Test
    void sinHerramientasReenviaElTextoComoEventosText() {
        geminiResponses.add(sse(textChunk("Hola "), textChunk("Adrian")));

        List<ServerSentEvent<String>> events = chat("hola");

        assertThat(names(events)).containsExactly("text", "text");
        assertThat(events.get(0).data()).isEqualTo("\"Hola \"");
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void ejecutaLaHerramientaYVuelveALlamarAGeminiConElResultado() {
        geminiResponses.add(sse(functionCallChunk("list_habits", Map.of())));
        geminiResponses.add(sse(textChunk("Llevas 4 dias meditando")));
        when(tools.execute(eq(USER_ID), eq("list_habits"), any()))
                .thenReturn(HabitChatTools.ToolResult.forModel(Map.of("habits", List.of())));

        List<ServerSentEvent<String>> events = chat("como voy?");

        assertThat(geminiCalls).isEqualTo(2);
        assertThat(names(events)).containsExactly("text");
        assertThat(events.get(0).data()).isEqualTo("\"Llevas 4 dias meditando\"");
    }

    @Test
    void lasSugerenciasLleganAlFrontendComoEventoSuggestions() {
        geminiResponses.add(sse(functionCallChunk("suggest_habits", Map.of())));
        geminiResponses.add(sse(textChunk("Ahi tienes un par de ideas")));
        when(tools.execute(eq(USER_ID), eq("suggest_habits"), any()))
                .thenReturn(new HabitChatTools.ToolResult(Map.of("shownToUser", 1), "suggestions",
                        List.of(new HabitSuggestion("Beber agua", "Al levantarte"))));

        List<ServerSentEvent<String>> events = chat("quiero cuidarme mas");

        assertThat(names(events)).containsExactly("suggestions", "text");
        assertThat(events.get(0).data()).contains("\"name\":\"Beber agua\"");
    }

    @Test
    void unErrorTransitorioDeGeminiSeReintentaSinQueElUsuarioLoNote() {
        geminiResponses.add(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("{}").build());
        geminiResponses.add(sse(textChunk("Hola")));

        List<ServerSentEvent<String>> events = chat("hola");

        assertThat(geminiCalls).isEqualTo(2);
        assertThat(names(events)).containsExactly("text");
    }

    @Test
    void unErrorNoTransitorioNoSeReintentaYTerminaEnUnEventoError() {
        geminiResponses.add(ClientResponse.create(HttpStatus.BAD_REQUEST).body("{}").build());

        List<ServerSentEvent<String>> events = chat("hola");

        assertThat(geminiCalls).isEqualTo(1);
        assertThat(names(events)).containsExactly("error");
    }

    @Test
    void elBucleSeCortaSiElModeloNoDejaDePedirHerramientas() {
        for (int i = 0; i < AiChatService.MAX_TOOL_ITERATIONS; i++) {
            geminiResponses.add(sse(functionCallChunk("list_habits", Map.of())));
        }
        when(tools.execute(eq(USER_ID), eq("list_habits"), any()))
                .thenReturn(HabitChatTools.ToolResult.forModel(Map.of("habits", List.of())));

        List<ServerSentEvent<String>> events = chat("hola");

        assertThat(geminiCalls).isEqualTo(AiChatService.MAX_TOOL_ITERATIONS);
        assertThat(names(events)).containsExactly("error");
        assertThat(events.get(0).data()).contains("demasiadas herramientas");
    }

    @Test
    void sinApiKeyDevuelveUnEventoErrorSinLlamarAGemini() {
        AiChatService service = service();
        ReflectionTestUtils.setField(service, "apiKey", "");

        List<ServerSentEvent<String>> events = service.streamChat(USER_ID, "hola").collectList().block();

        assertThat(geminiCalls).isZero();
        assertThat(names(events)).containsExactly("error");
    }

    // --- helpers ---

    private List<ServerSentEvent<String>> chat(String message) {
        // block(): en un test es aceptable esperar sincronamente al Flux
        // completo. En codigo de produccion reactivo, nunca.
        return service().streamChat(USER_ID, message).collectList().block(Duration.ofSeconds(10));
    }

    private AiChatService service() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://gemini.test")
                .exchangeFunction(request -> {
                    geminiCalls++;
                    return Mono.justOrEmpty(geminiResponses.poll());
                })
                .build();
        AiChatService service = new AiChatService(webClient, tools, objectMapper);
        // Los campos @Value solo los rellena Spring; aqui no hay contexto de
        // Spring, asi que los ponemos a mano por reflexion.
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "gemini-test");
        return service;
    }

    private static List<String> names(List<ServerSentEvent<String>> events) {
        return events.stream().map(ServerSentEvent::event).toList();
    }

    private static ClientResponse sse(String... dataChunks) {
        String body = Arrays.stream(dataChunks)
                .map(chunk -> "data: " + chunk + "\n\n")
                .collect(Collectors.joining());
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(body)
                .build();
    }

    private String textChunk(String text) {
        return modelChunk(Map.of("text", text));
    }

    private String functionCallChunk(String name, Map<String, Object> args) {
        return modelChunk(Map.of("functionCall", Map.of("name", name, "args", args)));
    }

    /** Misma forma que un evento real de streamGenerateContent. */
    private String modelChunk(Map<String, Object> part) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "candidates", List.of(Map.of(
                            "content", Map.of("role", "model", "parts", List.of(part))))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}