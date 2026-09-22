package com.adrian.habittracker.dto;

import java.util.List;

public record AiChatResponse(
        String reply,
        List<HabitSuggestion> suggestions
) {
}
