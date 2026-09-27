package com.adrian.habittracker.dto.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Misma forma que devuelve PushSubscription.toJSON() en el navegador:
 * { "endpoint": "...", "expirationTime": null, "keys": { "p256dh": "...", "auth": "..." } }
 * (expirationTime no lo usamos: Jackson ignora los campos que no existen en
 * el record, asi que el frontend puede mandar el objeto tal cual).
 */
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        // @Valid: sin esto, las validaciones del record anidado (@NotBlank
        // de p256dh/auth) no se comprobarian - Bean Validation no entra en
        // objetos anidados salvo que se lo pidas explicitamente.
        @Valid @NotNull Keys keys
) {
    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}