package com.adrian.habittracker.push;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushSenderTest {

    @Test
    void aceptaLosServiciosDePushDeLosNavegadores() {
        assertThat(WebPushSender.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(WebPushSender.isAllowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(WebPushSender.isAllowedEndpoint("https://web.push.apple.com/abc")).isTrue();
        assertThat(WebPushSender.isAllowedEndpoint("https://wns2-db5p.notify.windows.com/w/?token=abc")).isTrue();
    }

    @Test
    void rechazaCualquierOtraUrlParaEvitarSsrf() {
        assertThat(WebPushSender.isAllowedEndpoint("http://fcm.googleapis.com/fcm/send/abc")).isFalse(); // sin https
        assertThat(WebPushSender.isAllowedEndpoint("https://localhost:8080/actuator")).isFalse();
        assertThat(WebPushSender.isAllowedEndpoint("https://169.254.169.254/latest/meta-data")).isFalse();
        // Trampa clasica: el host "termina" parecido pero es otro dominio
        assertThat(WebPushSender.isAllowedEndpoint("https://fcm.googleapis.com.atacante.com/x")).isFalse();
        assertThat(WebPushSender.isAllowedEndpoint("https://evilpush.apple.com.atacante.com/x")).isFalse();
        assertThat(WebPushSender.isAllowedEndpoint("no es una url")).isFalse();
    }
}