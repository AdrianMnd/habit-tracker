package com.adrian.habittracker.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalControllerTest {

    @Test
    void soloAceptaElSecretoExacto() {
        assertThat(InternalController.secretMatches("s3cr3t-largo", "s3cr3t-largo")).isTrue();
        assertThat(InternalController.secretMatches("s3cr3t-largo", "s3cr3t-larg")).isFalse();
        assertThat(InternalController.secretMatches("s3cr3t-largo", null)).isFalse();
    }

    @Test
    void sinSecretoConfiguradoElEndpointQuedaCerradoParaTodos() {
        // Si no, con CRON_SECRET vacio bastaria mandar la cabecera vacia.
        assertThat(InternalController.secretMatches("", "")).isFalse();
        assertThat(InternalController.secretMatches(null, "loquesea")).isFalse();
    }
}