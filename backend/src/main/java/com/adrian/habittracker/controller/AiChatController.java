package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.AiChatRequest;
import com.adrian.habittracker.security.UserPrincipal;
import com.adrian.habittracker.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * Streaming de la respuesta del chat via Server-Sent Events. Con
     * Flux<String>, Spring envolvia cada elemento como un evento anonimo
     * ("data: ..."). Con Flux<ServerSentEvent<String>> controlamos cada
     * evento entero, incluido su nombre ("event: text", "event: suggestions"...),
     * que es lo que permite al frontend distinguir tipos de evento.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody AiChatRequest request,
                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        return aiChatService.streamChat(currentUser.getId(), request.message());
    }
}
