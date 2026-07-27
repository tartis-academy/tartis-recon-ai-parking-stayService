package com.tartis_recon_ai_parking.domain.stay.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class InvalidStayExceptionTest {

    @Test
    void shouldExposeMessage() {
        InvalidStayException ex = new InvalidStayException("mensaje de dominio");

        assertEquals("mensaje de dominio", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void shouldExposeMessageAndCause() {
        Throwable cause = new IllegalStateException("causa original");

        InvalidStayException ex = new InvalidStayException("mensaje con causa", cause);

        assertEquals("mensaje con causa", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
