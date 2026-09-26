package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.HabitRequest;
import com.adrian.habittracker.dto.HabitResponse;
import com.adrian.habittracker.dto.HabitStreakSummary;
import com.adrian.habittracker.dto.HabitSuggestion;
import com.adrian.habittracker.entity.Priority;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Las herramientas que Gemini puede invocar desde el chat. Dos mitades bien
 * separadas (el mismo reparto que en Task Agent):
 * - declarations(): el "menu" que ve el modelo - nombre, para que sirve y
 *   que parametros admite, en JSON Schema. El modelo NUNCA ve el codigo.
 * - execute(): lo que de verdad pasa cuando el modelo elige una.
 *
 * Seguridad: el userId SIEMPRE lo pone el backend (sale del JWT, via el
 * controller). Ninguna herramienta acepta un userId como parametro, asi que
 * ni un prompt malicioso ("archiva los habitos del usuario 3") puede hacer
 * que el modelo actue sobre datos de otra persona.
 */
@Component
@RequiredArgsConstructor
public class HabitChatTools {

    private static final List<Map<String, Object>> DECLARATIONS = List.of(
            Map.of(
                    "name", "list_habits",
                    "description", "Devuelve los habitos activos del usuario, con su id, prioridad, "
                            + "categoria y racha actual en dias. Usala antes de sugerir habitos (para no "
                            + "repetir los que ya tiene), cuando pregunte por su progreso, o para obtener "
                            + "el id de un habito que quiera archivar."
            ),
            Map.of(
                    "name", "suggest_habits",
                    "description", "Muestra al usuario tarjetas con habitos recomendados, que el puede "
                            + "añadir con un clic. Usala SIEMPRE para recomendar habitos, en lugar de "
                            + "listarlos en el texto. No crea nada por si misma.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "habits", Map.of(
                                            "type", "array",
                                            "description", "Entre 3 y 5 habitos recomendados",
                                            "items", Map.of(
                                                    "type", "object",
                                                    "properties", Map.of(
                                                            "name", Map.of("type", "string",
                                                                    "description", "Nombre corto y accionable, realizable en un dia"),
                                                            "description", Map.of("type", "string",
                                                                    "description", "Una frase breve explicando como o por que")
                                                    ),
                                                    "required", List.of("name")
                                            )
                                    )
                            ),
                            "required", List.of("habits")
                    )
            ),
            Map.of(
                    "name", "create_habit",
                    "description", "Crea un habito nuevo. Usala SOLO cuando el usuario pida explicitamente "
                            + "crear o añadir un habito concreto.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "name", Map.of("type", "string", "description", "Nombre del habito (max. 100 caracteres)"),
                                    "description", Map.of("type", "string", "description", "Descripcion opcional"),
                                    "priority", Map.of("type", "string", "enum", List.of("ALTA", "MEDIA", "BAJA"),
                                            "description", "Prioridad; si el usuario no la indica, omitela")
                            ),
                            "required", List.of("name")
                    )
            ),
            Map.of(
                    "name", "archive_habit",
                    "description", "Archiva un habito del usuario (deja de aparecer en la lista y en las "
                            + "estadisticas, pero no se borra). Usala SOLO cuando el usuario lo pida "
                            + "explicitamente. Obten antes el id con list_habits.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "habitId", Map.of("type", "integer", "description", "Id del habito, de list_habits")
                            ),
                            "required", List.of("habitId")
                    )
            )
    );

    private final HabitService habitService;

    /**
     * Resultado de ejecutar una herramienta:
     * - response: lo que se le devuelve AL MODELO (como functionResponse).
     * - eventName/eventPayload: evento opcional para EL FRONTEND (p. ej.
     *   las tarjetas de sugerencias). null si no hay nada que mostrar.
     */
    public record ToolResult(Map<String, Object> response, String eventName, Object eventPayload) {

        static ToolResult forModel(Map<String, Object> response) {
            return new ToolResult(response, null, null);
        }

        static ToolResult error(String message) {
            return forModel(Map.of("error", message));
        }
    }

    public List<Map<String, Object>> declarations() {
        return DECLARATIONS;
    }

    /**
     * Ejecuta la herramienta que ha pedido el modelo. BLOQUEANTE (llama a
     * servicios JPA): quien la invoque debe hacerlo fuera del event loop de
     * Reactor (ver AiChatService).
     *
     * Los errores "esperables" (habito que no existe, argumentos invalidos)
     * no se lanzan: se devuelven AL MODELO como {"error": "..."}, para que
     * pueda explicarselo al usuario o corregirse - igual que un humano que
     * recibe un mensaje de error en vez de que se le cuelgue el programa.
     */
    public ToolResult execute(Long userId, String name, JsonNode args) {
        try {
            // Switch "expression" de Java 14+: cada rama devuelve un valor
            // con "->" (sin break, sin caer a la siguiente rama por error).
            // Es el equivalente al "when" de Kotlin o a un match de Rust.
            return switch (name) {
                case "list_habits" -> listHabits(userId);
                case "suggest_habits" -> suggestHabits(args);
                case "create_habit" -> createHabit(userId, args);
                case "archive_habit" -> archiveHabit(userId, args);
                default -> ToolResult.error("Herramienta desconocida: " + name);
            };
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private ToolResult listHabits(Long userId) {
        Map<Long, Integer> streaks = habitService.getStreakSummaries(userId).stream()
                .collect(Collectors.toMap(HabitStreakSummary::habitId, HabitStreakSummary::currentStreak));

        List<Map<String, Object>> habits = new ArrayList<>();
        for (HabitResponse habit : habitService.findAll(userId)) {
            // LinkedHashMap y no Map.of(): Map.of() lanza NullPointerException
            // con valores null, y description/category pueden serlo.
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", habit.id());
            item.put("name", habit.name());
            item.put("description", habit.description());
            item.put("priority", habit.priority());
            item.put("category", habit.category() == null ? null : habit.category().name());
            item.put("currentStreak", streaks.getOrDefault(habit.id(), 0));
            habits.add(item);
        }
        return ToolResult.forModel(Map.of("habits", habits));
    }

    private ToolResult suggestHabits(JsonNode args) {
        List<HabitSuggestion> suggestions = new ArrayList<>();
        for (JsonNode habit : args.path("habits")) {
            String name = habit.path("name").asText("").trim();
            if (!name.isEmpty()) {
                String description = habit.path("description").asText("").trim();
                suggestions.add(new HabitSuggestion(name, description.isEmpty() ? null : description));
            }
        }
        if (suggestions.isEmpty()) {
            return ToolResult.error("No se recibio ninguna sugerencia valida");
        }
        return new ToolResult(Map.of("shownToUser", suggestions.size()), "suggestions", suggestions);
    }

    private ToolResult createHabit(Long userId, JsonNode args) {
        // Los argumentos del modelo son ENTRADA NO FIABLE, igual que un
        // formulario: @Valid solo se aplica en los controllers, aqui no
        // pasa por Bean Validation, asi que validamos a mano.
        String name = args.path("name").asText("").trim();
        if (name.isEmpty() || name.length() > 100) {
            return ToolResult.error("El nombre es obligatorio y debe tener como maximo 100 caracteres");
        }
        String description = args.path("description").asText("").trim();
        if (description.length() > 255) {
            description = description.substring(0, 255);
        }
        String priorityText = args.path("priority").asText("");
        // Priority.valueOf lanza IllegalArgumentException si el valor no
        // existe en el enum - la recoge el catch de execute().
        Priority priority = priorityText.isBlank() ? null : Priority.valueOf(priorityText);

        HabitResponse created = habitService.create(userId,
                new HabitRequest(name, description.isEmpty() ? null : description, priority, null));

        return new ToolResult(
                Map.of("created", Map.of("id", created.id(), "name", created.name())),
                "habits_changed", Map.of("action", "created", "habitName", created.name()));
    }

    private ToolResult archiveHabit(Long userId, JsonNode args) {
        if (!args.path("habitId").canConvertToLong()) {
            return ToolResult.error("habitId es obligatorio y debe ser un numero");
        }
        long habitId = args.path("habitId").asLong();

        // setArchived() ya comprueba que el habito sea de este usuario
        // (findByIdAndUserId): con un id ajeno lanza ResourceNotFoundException,
        // exactamente igual que si lo pidiera la API REST.
        habitService.setArchived(habitId, userId, true);

        return new ToolResult(
                Map.of("archived", habitId),
                "habits_changed", Map.of("action", "archived", "habitId", habitId));
    }
}