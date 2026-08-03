package com.tartis_recon_ai_parking.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /**
     * El MDC es estatico y por hilo: si un test lo deja sucio, contamina a los
     * siguientes. Se limpia siempre, aunque el test falle.
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * El valor del MDC solo existe mientras la cadena se ejecuta, asi que hay
     * que leerlo desde dentro. Este FilterChain lo captura al pasar.
     */
    private FilterChain chainCapturing(AtomicReference<String> seen) {
        return (req, res) -> seen.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    }

    @Test
    @DisplayName("Debe reutilizar el correlation-id que envia Kong en la cabecera")
    void shouldReuseIncomingCorrelationId() throws ServletException, IOException {
        String fromKong = "3f2a9c11-8e4d-4b7a-9c1e-2d5f6a7b8c90";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, fromKong);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturing(insideChain));

        assertEquals(fromKong, insideChain.get(),
                "el id de Kong debe llegar al MDC tal cual, o los logs no se pueden unir");
        assertEquals(fromKong, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    @DisplayName("Debe aceptar el formato uuid#counter que genera Kong")
    void shouldAcceptKongUuidCounterFormat() throws ServletException, IOException {
        String uuidCounter = "3f2a9c11-8e4d-4b7a-9c1e-2d5f6a7b8c90#42";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, uuidCounter);
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chainCapturing(insideChain));

        assertEquals(uuidCounter, insideChain.get());
    }

    @Test
    @DisplayName("Debe generar un correlation-id si la peticion no trae la cabecera (llamada directa al puerto, sin Kong)")
    void shouldGenerateCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, chainCapturing(insideChain));

        assertNotNull(insideChain.get(), "sin cabecera tambien tiene que haber traza");
        assertDoesNotThrow(() -> UUID.fromString(insideChain.get()));
        assertEquals(insideChain.get(), response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    @DisplayName("Debe descartar una cabecera con saltos de linea y generar una nueva (log injection)")
    void shouldRejectCorrelationIdWithControlCharacters() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER,
                "valido\n2026-07-30 ERROR linea de log falsa");
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chainCapturing(insideChain));

        assertDoesNotThrow(() -> UUID.fromString(insideChain.get()),
                "una cabecera con caracteres de control debe descartarse, no propagarse a los logs");
    }

    @Test
    @DisplayName("Debe descartar una cabecera mas larga de 128 caracteres")
    void shouldRejectOversizedCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "a".repeat(129));
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chainCapturing(insideChain));

        assertDoesNotThrow(() -> UUID.fromString(insideChain.get()));
    }

    @Test
    @DisplayName("Debe descartar una cabecera vacia")
    void shouldRejectBlankCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "");
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chainCapturing(insideChain));

        assertDoesNotThrow(() -> UUID.fromString(insideChain.get()));
    }

    @Test
    @DisplayName("Debe limpiar el MDC al terminar, para que el hilo del pool no arrastre el id a la siguiente peticion")
    void shouldClearMdcAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    }

    @Test
    @DisplayName("Debe limpiar el MDC tambien si la cadena lanza una excepcion")
    void shouldClearMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123");
        FilterChain explodingChain = (req, res) -> {
            throw new ServletException("fallo aguas abajo");
        };

        assertThrows(ServletException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), explodingChain));

        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                "el finally debe limpiar el MDC aunque la peticion acabe en error");
    }

    @Test
    @DisplayName("No debe reutilizar el mismo id en dos peticiones sin cabecera")
    void shouldGenerateDistinctIdsForSeparateRequests() throws ServletException, IOException {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chainCapturing(first));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chainCapturing(second));

        assertTrue(first.get() != null && !first.get().equals(second.get()),
                "cada peticion sin cabecera necesita su propio id");
    }
}
