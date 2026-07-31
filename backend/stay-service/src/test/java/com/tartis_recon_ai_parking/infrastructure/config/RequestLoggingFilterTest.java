package com.tartis_recon_ai_parking.infrastructure.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {

    private static final String JWT_DE_PRUEBA =
            "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJvcGVyYXJpbyJ9.firma-que-no-debe-acabar-en-los-logs";

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    private ch.qos.logback.classic.Logger accessLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level nivelPrevio;

    @BeforeEach
    void captureAccessLog() {
        accessLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("http.access");
        nivelPrevio = accessLogger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        accessLogger.addAppender(appender);
        accessLogger.setLevel(Level.INFO);
    }

    /** El logger es global: hay que dejarlo como estaba o se contaminan otros tests. */
    @AfterEach
    void cleanUp() {
        accessLogger.detachAppender(appender);
        appender.stop();
        accessLogger.setLevel(nivelPrevio);
        MDC.clear();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private String singleLine() {
        assertEquals(1, appender.list.size(), "se espera exactamente una linea de acceso");
        return appender.list.get(0).getFormattedMessage();
    }

    @Test
    @DisplayName("Debe registrar metodo, path, status y latencia de cada peticion")
    void shouldLogRequestDetails() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request("POST", "/v1/stays/check-in"), response, (req, res) -> { });

        String line = singleLine();
        assertTrue(line.contains("method=POST"), line);
        assertTrue(line.contains("path=/v1/stays/check-in"), line);
        assertTrue(line.contains("status=201"), line);
        assertTrue(line.matches(".*duration_ms=\\d+.*"), line);
    }

    /**
     * El test importante de esta clase. stay-service acepta el token por query
     * string (SecurityConfig, necesario para SSE-08), asi que registrar la query
     * escribiria JWTs completos en los logs del contenedor. Si alguien cambia
     * getRequestURI() por getRequestURL()+getQueryString() "para tener mas
     * informacion", este test salta.
     */
    @Test
    @DisplayName("NUNCA debe registrar la query string, donde puede viajar el token")
    void shouldNeverLogTheQueryString() throws ServletException, IOException {
        MockHttpServletRequest request = request("GET", "/v1/stays");
        request.setQueryString("jwt=" + JWT_DE_PRUEBA);
        request.addParameter("jwt", JWT_DE_PRUEBA);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        String line = singleLine();
        assertTrue(line.contains("path=/v1/stays"), line);
        assertFalse(line.contains("eyJ"), "un JWT no puede acabar en los logs: " + line);
        assertFalse(line.contains("jwt="), line);
    }

    @Test
    @DisplayName("Debe incluir la identidad que dejo RequestIdentityFilter en el MDC")
    void shouldIncludeIdentityFromMdc() throws ServletException, IOException {
        MDC.put(RequestIdentityFilter.USER_NAME_MDC_KEY, "operario.test");
        MDC.put(RequestIdentityFilter.ROLES_MDC_KEY, "OPERARIO");
        MDC.put(RequestIdentityFilter.CLIENT_ID_MDC_KEY, "parking-frontend");

        filter.doFilter(request("GET", "/v1/stays"), new MockHttpServletResponse(), (req, res) -> { });

        String line = singleLine();
        assertTrue(line.contains("user=operario.test"), line);
        assertTrue(line.contains("roles=OPERARIO"), line);
        assertTrue(line.contains("client=parking-frontend"), line);
    }

    @Test
    @DisplayName("Debe marcar la peticion como anonima si no hay identidad en el MDC")
    void shouldFallBackToAnonymous() throws ServletException, IOException {
        filter.doFilter(request("GET", "/v1/stays"), new MockHttpServletResponse(), (req, res) -> { });

        String line = singleLine();
        assertTrue(line.contains("user=anonymous"), line);
        assertTrue(line.contains("roles=-"), line);
    }

    /**
     * Es la peticion que mas interesa registrar, asi que la linea sale igual y
     * despues se propaga la excepcion.
     */
    @Test
    @DisplayName("Debe registrar la peticion tambien cuando la cadena lanza una excepcion")
    void shouldLogWhenChainThrows() {
        FilterChain explodingChain = (req, res) -> {
            throw new ServletException("fallo aguas abajo");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(
                request("POST", "/v1/stays/check-in"), new MockHttpServletResponse(), explodingChain));

        assertTrue(singleLine().contains("path=/v1/stays/check-in"));
    }

    @Test
    @DisplayName("No debe registrar los healthcheck: pegan cada pocos segundos y ahogan el log util")
    void shouldNotLogHealthChecks() throws ServletException, IOException {
        filter.doFilter(request("GET", "/actuator/health/readiness"),
                new MockHttpServletResponse(), (req, res) -> { });

        assertTrue(appender.list.isEmpty());
    }

    @Test
    @DisplayName("No debe registrar la ruta SSE: es una conexion de larga duracion")
    void shouldNotLogSseRoute() throws ServletException, IOException {
        filter.doFilter(request("GET", "/v1/stays/events"),
                new MockHttpServletResponse(), (req, res) -> { });

        assertTrue(appender.list.isEmpty(),
                "una unica linea al cerrarse el stream, con una duracion de 40 minutos, no informa de nada");
    }

    @Test
    @DisplayName("No debe registrar la documentacion (openapi, swagger)")
    void shouldNotLogDocumentation() throws ServletException, IOException {
        filter.doFilter(request("GET", "/openapi.yml"), new MockHttpServletResponse(), (req, res) -> { });
        filter.doFilter(request("GET", "/swagger-ui/index.html"), new MockHttpServletResponse(), (req, res) -> { });
        filter.doFilter(request("GET", "/v3/api-docs"), new MockHttpServletResponse(), (req, res) -> { });

        assertTrue(appender.list.isEmpty());
    }

    @Test
    @DisplayName("Debe registrar el resto de rutas de negocio")
    void shouldLogBusinessRoutes() throws ServletException, IOException {
        filter.doFilter(request("GET", "/v1/stays/9e1f"), new MockHttpServletResponse(), (req, res) -> { });

        assertEquals(1, appender.list.size());
    }
}
