package com.tartis_recon_ai_parking.domain.stay.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoActiveTariffExceptionTest {

    @Test
    void shouldExposeMessage() {
        NoActiveTariffException ex = new NoActiveTariffException("sin tarifa activa para CAR (IN-08)");

        assertEquals("sin tarifa activa para CAR (IN-08)", ex.getMessage());
    }
}
