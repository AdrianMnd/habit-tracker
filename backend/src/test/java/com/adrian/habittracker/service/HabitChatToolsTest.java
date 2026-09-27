package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.HabitRequest;
import com.adrian.habittracker.dto.HabitResponse;
import com.adrian.habittracker.dto.HabitStreakSummary;
import com.adrian.habittracker.dto.HabitSuggestion;
import com.adrian.habittracker.entity.Priority;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitChatToolsTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HabitService habitService;

    @InjectMocks
    private HabitChatTools tools;

    @Test
    void listHabitsIncluyeLaRachaActualDeCadaHabito() {
        when(habitService.findAll(USER_ID)).thenReturn(List.of(habit(1L, "Meditar")));
        when(habitService.getStreakSummaries(USER_ID)).thenReturn(List.of(new HabitStreakSummary(1L, "Meditar", 4)));

        HabitChatTools.ToolResult result = tools.execute(USER_ID, "list_habits", args("{}"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> habits = (List<Map<String, Object>>) result.response().get("habits");
        assertThat(habits.get(0))
                .containsEntry("id", 1L)
                .containsEntry("name", "Meditar")
                .containsEntry("currentStreak", 4);
        // Solo informa al modelo: nada que pintar en el frontend.
        assertThat(result.eventName()).isNull();
    }

    @Test
    void suggestHabitsDescartaLasSugerenciasSinNombreYEmiteEventoParaElFrontend() {
        HabitChatTools.ToolResult result = tools.execute(USER_ID, "suggest_habits", args("""
                {"habits": [
                  {"name": "Leer 10 minutos", "description": "Antes de dormir"},
                  {"name": "   "}
                ]}"""));

        assertThat(result.eventName()).isEqualTo("suggestions");
        assertThat(result.eventPayload()).isEqualTo(List.of(new HabitSuggestion("Leer 10 minutos", "Antes de dormir")));
        assertThat(result.response()).containsEntry("shownToUser", 1);
    }

    @Test
    void createHabitUsaElUsuarioDelBackendYAvisaAlFrontend() {
        when(habitService.create(eq(USER_ID), any())).thenReturn(habit(10L, "Beber agua"));

        HabitChatTools.ToolResult result = tools.execute(USER_ID, "create_habit",
                args("{\"name\": \"Beber agua\", \"priority\": \"ALTA\"}"));

        ArgumentCaptor<HabitRequest> captor = ArgumentCaptor.forClass(HabitRequest.class);
        verify(habitService).create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Beber agua");
        assertThat(captor.getValue().priority()).isEqualTo(Priority.ALTA);
        assertThat(result.eventName()).isEqualTo("habits_changed");
    }

    @Test
    void createHabitRechazaUnNombreVacioSinTocarLaBaseDeDatos() {
        HabitChatTools.ToolResult result = tools.execute(USER_ID, "create_habit", args("{\"name\": \"  \"}"));

        assertThat(result.response()).containsKey("error");
        verify(habitService, never()).create(any(), any());
    }

    @Test
    void unaPrioridadQueNoExisteSeDevuelveComoErrorAlModelo() {
        HabitChatTools.ToolResult result = tools.execute(USER_ID, "create_habit",
                args("{\"name\": \"Correr\", \"priority\": \"URGENTISIMA\"}"));

        assertThat(result.response()).containsKey("error");
        verify(habitService, never()).create(any(), any());
    }

    @Test
    void archivarUnHabitoAjenoDevuelveErrorAlModeloEnVezDeLanzar() {
        doThrow(new ResourceNotFoundException("Habito no encontrado"))
                .when(habitService).setArchived(99L, USER_ID, true);

        HabitChatTools.ToolResult result = tools.execute(USER_ID, "archive_habit", args("{\"habitId\": 99}"));

        assertThat(result.response()).containsEntry("error", "Habito no encontrado");
        assertThat(result.eventName()).isNull();
    }

    @Test
    void unaHerramientaDesconocidaDevuelveError() {
        HabitChatTools.ToolResult result = tools.execute(USER_ID, "delete_everything", args("{}"));

        assertThat(result.response()).containsKey("error");
    }

    private JsonNode args(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private HabitResponse habit(Long id, String name) {
        return new HabitResponse(id, name, null, Priority.MEDIA, null, Instant.now());
    }
}