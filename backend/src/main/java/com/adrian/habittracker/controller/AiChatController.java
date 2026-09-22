package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.AiChatRequest;
import com.adrian.habittracker.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * Streaming de la respuesta del chat via Server-Sent Events. Spring
     * envuelve cada elemento del Flux como un evento "data: <texto>\n\n"
     * automaticamente al declarar produces = TEXT_EVENT_STREAM_VALUE con
     * un tipo de retorno Flux<String>.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@Valid @RequestBody AiChatRequest request) {
        return aiChatService.streamChat(request.message());
    }
}
