package com.adrian.habittracker.dto.push;

/**
 * Lo que el frontend necesita para suscribirse: si el push esta disponible
 * en este servidor y, en ese caso, la clave publica VAPID (es PUBLICA a
 * proposito: el navegador la usa para atar la suscripcion a este servidor).
 */
public record PushConfigResponse(boolean enabled, String publicKey) {
}