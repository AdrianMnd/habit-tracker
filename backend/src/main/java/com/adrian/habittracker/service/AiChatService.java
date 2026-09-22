package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.AiChatResponse;
import com.adrian.habittracker.dto.HabitResponse;
import com.adrian.habittracker.exception.AiServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el prompt para el chat de recomendacion de habitos y habla con
 * la API de Gemini (endpoint generateContent).
 * <p>
 * Le pedimos a Gemini que devuelva JSON estructurado directamente
 * (generationConfig.responseSchema), en vez de confiar en que el modelo
 * "se porte bien" solo con instrucciones en el prompt.
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

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

            Reglas:
            - Responde siempre en español, con un tono cercano, breve y motivador
              (evita parrafos largos).
            - Cada habito sugerido debe ser especifico y realizable en un solo dia
              (evita objetivos vagos como "ser mas saludable").
            - No repitas habitos que el usuario ya tiene registrados.
            - Si no tienes ninguna sugerencia concreta que aportar, devuelve la
              lista de sugerencias vacia; no la rellenes por rellenar.
            """;

    private final WebClient geminiWebClient;
    private final HabitService habitService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String model;

    public AiChatResponse chat(String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException("La clave de la API de Gemini no esta configurada (GEMINI_API_KEY)");
        }

        String habitsContext = buildHabitsContext();
        Map<String, Object> requestBody = buildRequestBody(habitsContext, userMessage);

        Map<?, ?> rawResponse = geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(20))
                .block();

        String jsonText = extractText(rawResponse);
        return parseResponse(jsonText);
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
        body.put("generationConfig", generationConfig());
        return body;
    }

    private Map<String, Object> generationConfig() {
        Map<String, Object> nameProp = Map.of("type", "STRING");
        Map<String, Object> descriptionProp = Map.of("type", "STRING");

        Map<String, Object> suggestionItem = new LinkedHashMap<>();
        suggestionItem.put("type", "OBJECT");
        suggestionItem.put("properties", Map.of("name", nameProp, "description", descriptionProp));
        suggestionItem.put("required", List.of("name"));

        Map<String, Object> suggestionsArray = new LinkedHashMap<>();
        suggestionsArray.put("type", "ARRAY");
        suggestionsArray.put("items", suggestionItem);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", Map.of("reply", Map.of("type", "STRING"), "suggestions", suggestionsArray));
        schema.put("required", List.of("reply", "suggestions"));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("responseMimeType", "application/json");
        config.put("responseSchema", schema);
        return config;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> rawResponse) {
        try {
            List<Object> candidates = (List<Object>) rawResponse.get("candidates");
            Map<String, Object> firstCandidate = (Map<String, Object>) candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            List<Object> parts = (List<Object>) content.get("parts");
            Map<String, Object> firstPart = (Map<String, Object>) parts.get(0);
            return (String) firstPart.get("text");
        } catch (RuntimeException e) {
            throw new AiServiceException("Respuesta inesperada de Gemini: " + rawResponse, e);
        }
    }

    private AiChatResponse parseResponse(String jsonText) {
        try {
            return objectMapper.readValue(jsonText, AiChatResponse.class);
        } catch (Exception e) {
            throw new AiServiceException("No se pudo interpretar la respuesta de Gemini", e);
        }
    }
}
