package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.push.PushConfigResponse;
import com.adrian.habittracker.dto.push.PushSubscriptionRequest;
import com.adrian.habittracker.dto.push.ReminderSettingsRequest;
import com.adrian.habittracker.dto.push.ReminderSettingsResponse;
import com.adrian.habittracker.dto.push.UnsubscribeRequest;
import com.adrian.habittracker.security.UserPrincipal;
import com.adrian.habittracker.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @GetMapping("/config")
    public PushConfigResponse config() {
        return pushService.config();
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                          @AuthenticationPrincipal UserPrincipal currentUser) {
        pushService.subscribe(currentUser.getId(), request);
    }

    // DELETE con body: poco habitual, pero valido en HTTP y soportado por
    // fetch y Spring. El endpoint es una URL larga llena de caracteres
    // especiales - meterlo en la ruta (/subscriptions/{endpoint}) obligaria
    // a codificarlo y lo dejaria registrado en los logs de acceso.
    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request,
                            @AuthenticationPrincipal UserPrincipal currentUser) {
        pushService.unsubscribe(currentUser.getId(), request.endpoint());
    }

    @GetMapping("/reminder")
    public ReminderSettingsResponse getReminder(@AuthenticationPrincipal UserPrincipal currentUser) {
        return pushService.getSettings(currentUser.getId());
    }

    @PutMapping("/reminder")
    public ReminderSettingsResponse updateReminder(@Valid @RequestBody ReminderSettingsRequest request,
                                                   @AuthenticationPrincipal UserPrincipal currentUser) {
        return pushService.updateSettings(currentUser.getId(), request);
    }

    @PostMapping("/test")
    public Map<String, Integer> sendTest(@AuthenticationPrincipal UserPrincipal currentUser) {
        return Map.of("delivered", pushService.sendTest(currentUser.getId()));
    }
}