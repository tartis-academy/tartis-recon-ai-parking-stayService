package com.tartis_recon_ai_parking.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestIdentityFilterTest {

    private final RequestIdentityFilter filter = new RequestIdentityFilter();

    /**
     * Tanto el MDC como el SecurityContext son estaticos y por hilo: si un test
     * los deja sucios, contaminan a los siguientes. Se limpian siempre, aunque
     * el test falle.
     */
    @AfterEach
    void clearContext() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(String sub, String preferredUsername, String azp) {
        Jwt.Builder builder = Jwt.withTokenValue("no-se-usa")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject(sub);
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return builder.build();
    }

    private static void authenticateWith(Jwt token, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(token, authorities));
    }

    /** El MDC solo existe mientras la cadena corre, asi que se lee desde dentro. */
    private FilterChain chainCapturing(Map<String, AtomicReference<String>> seen) {
        return (req, res) -> seen.forEach((key, ref) -> ref.set(MDC.get(key)));
    }

    @Test
    @DisplayName("Debe volcar sub, preferred_username, azp y roles al MDC")
    void shouldPopulateMdcFromJwt() throws ServletException, IOException {
        authenticateWith(
                jwt("f81d4fae-7dec-11d0-a765-00a0c91e6bf6", "operario.test", "parking-frontend"),
                "ROLE_OPERARIO");

        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> userName = new AtomicReference<>();
        AtomicReference<String> clientId = new AtomicReference<>();
        AtomicReference<String> roles = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(
                        RequestIdentityFilter.USER_ID_MDC_KEY, userId,
                        RequestIdentityFilter.USER_NAME_MDC_KEY, userName,
                        RequestIdentityFilter.CLIENT_ID_MDC_KEY, clientId,
                        RequestIdentityFilter.ROLES_MDC_KEY, roles)));

        assertEquals("f81d4fae-7dec-11d0-a765-00a0c91e6bf6", userId.get());
        assertEquals("operario.test", userName.get());
        assertEquals("parking-frontend", clientId.get());
        assertEquals("OPERARIO", roles.get());
    }

    @Test
    @DisplayName("Debe quitar el prefijo ROLE_ y ordenar los roles alfabeticamente")
    void shouldStripRolePrefixAndSortRoles() throws ServletException, IOException {
        authenticateWith(jwt("sub", "admin.test", "parking-frontend"),
                "ROLE_OPERARIO", "ROLE_ADMIN");

        AtomicReference<String> roles = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.ROLES_MDC_KEY, roles)));

        // Ordenados: Keycloak no garantiza el orden de realm_access.roles y sin
        // ordenar la misma peticion saldria unas veces "ADMIN,OPERARIO" y otras
        // al reves, rompiendo cualquier agrupacion sobre el campo.
        assertEquals("ADMIN,OPERARIO", roles.get());
    }

    @Test
    @DisplayName("Debe descartar authorities que no sean roles")
    void shouldIgnoreNonRoleAuthorities() throws ServletException, IOException {
        authenticateWith(jwt("sub", "admin.test", "parking-frontend"),
                "ROLE_ADMIN", "SCOPE_profile", "SCOPE_email");

        AtomicReference<String> roles = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.ROLES_MDC_KEY, roles)));

        assertEquals("ADMIN", roles.get());
    }

    @Test
    @DisplayName("Debe identificar al servicio cuando la llamada viene con client_credentials")
    void shouldIdentifyServiceAccountCalls() throws ServletException, IOException {
        authenticateWith(
                jwt("sub-servicio", "service-account-parking-stay-service", "parking-stay-service"),
                "ROLE_ADMIN");

        AtomicReference<String> clientId = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.CLIENT_ID_MDC_KEY, clientId)));

        // Es lo que distingue "lo pidio un operario desde el front" de "lo pidio
        // otro microservicio", que es media respuesta a "quien llama a que".
        assertEquals("parking-stay-service", clientId.get());
    }

    @Test
    @DisplayName("No debe meter nada si la peticion no esta autenticada (actuator, perfil dev)")
    void shouldPutNothingWhenNotAuthenticated() throws ServletException, IOException {
        AtomicReference<String> userName = new AtomicReference<>("valor-previo");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.USER_NAME_MDC_KEY, userName)));

        assertNull(userName.get(), "sin autenticacion se deja el hueco vacio, no se inventa nada");
    }

    @Test
    @DisplayName("No debe meter nada si la autenticacion no es un JwtAuthenticationToken")
    void shouldPutNothingForNonJwtAuthentication() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alguien", "credenciales", List.of()));

        AtomicReference<String> userName = new AtomicReference<>("valor-previo");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.USER_NAME_MDC_KEY, userName)));

        assertNull(userName.get());
    }

    @Test
    @DisplayName("Debe tolerar un token sin preferred_username ni azp")
    void shouldToleratePartialClaims() throws ServletException, IOException {
        authenticateWith(jwt("solo-sub", null, null), "ROLE_ADMIN");

        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> userName = new AtomicReference<>("valor-previo");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chainCapturing(Map.of(
                        RequestIdentityFilter.USER_ID_MDC_KEY, userId,
                        RequestIdentityFilter.USER_NAME_MDC_KEY, userName)));

        assertEquals("solo-sub", userId.get());
        assertNull(userName.get(), "un claim ausente deja el hueco vacio, no escribe 'null'");
    }

    @Test
    @DisplayName("Debe limpiar el MDC al terminar, para que el hilo del pool no arrastre la identidad")
    void shouldClearMdcAfterRequest() throws ServletException, IOException {
        authenticateWith(jwt("sub", "operario.test", "parking-frontend"), "ROLE_OPERARIO");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });

        assertNull(MDC.get(RequestIdentityFilter.USER_ID_MDC_KEY));
        assertNull(MDC.get(RequestIdentityFilter.USER_NAME_MDC_KEY));
        assertNull(MDC.get(RequestIdentityFilter.CLIENT_ID_MDC_KEY));
        assertNull(MDC.get(RequestIdentityFilter.ROLES_MDC_KEY));
    }

    @Test
    @DisplayName("Debe limpiar el MDC tambien si la cadena lanza una excepcion")
    void shouldClearMdcWhenChainThrows() {
        authenticateWith(jwt("sub", "operario.test", "parking-frontend"), "ROLE_OPERARIO");

        FilterChain explodingChain = (req, res) -> {
            throw new ServletException("fallo aguas abajo");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), explodingChain));

        assertNull(MDC.get(RequestIdentityFilter.USER_NAME_MDC_KEY));
    }

    @Test
    @DisplayName("No debe pisar el correlationId que dejo CorrelationIdFilter")
    void shouldNotRemoveCorrelationId() throws ServletException, IOException {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "abc-123");
        authenticateWith(jwt("sub", "operario.test", "parking-frontend"), "ROLE_OPERARIO");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });

        // Se quitan claves concretas y no MDC.clear() precisamente por esto: el
        // filtro de correlacion es el dueno de esa clave y la limpia el.
        assertEquals("abc-123", MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    }

    @Test
    @DisplayName("Debe recoger X-Origin-User cuando la llamada viene de otro microservicio")
    void shouldReadOriginUserHeader() throws ServletException, IOException {
        // Escenario real: stay llama a este servicio con su token de
        // client_credentials, asi que azp es la cuenta de servicio y el operario
        // que origino la operacion solo viaja en la cabecera.
        authenticateWith(
                jwt("sub-servicio", "service-account-parking-stay-service", "parking-stay-service"),
                "ROLE_ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdentityFilter.ORIGIN_USER_HEADER, "operario.test");

        AtomicReference<String> originUser = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.ORIGIN_USER_MDC_KEY, originUser)));

        assertEquals("operario.test", originUser.get());
    }

    @Test
    @DisplayName("Debe guardar X-Origin-User en una clave distinta de userName, que si sale del token")
    void shouldKeepOriginUserSeparateFromVerifiedIdentity() throws ServletException, IOException {
        authenticateWith(
                jwt("sub-servicio", "service-account-parking-stay-service", "parking-stay-service"),
                "ROLE_ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdentityFilter.ORIGIN_USER_HEADER, "operario.test");

        AtomicReference<String> userName = new AtomicReference<>();
        AtomicReference<String> originUser = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                chainCapturing(Map.of(
                        RequestIdentityFilter.USER_NAME_MDC_KEY, userName,
                        RequestIdentityFilter.ORIGIN_USER_MDC_KEY, originUser)));

        // userName es identidad verificada (sale del JWT); originUser es contexto
        // que manda otro servicio y no va firmado. Mezclarlos haria creer que la
        // segunda esta tan garantizada como la primera.
        assertEquals("service-account-parking-stay-service", userName.get());
        assertEquals("operario.test", originUser.get());
    }

    /**
     * La cabecera llega de fuera y va directa a los logs. Sin validarla, un
     * cliente podria colar saltos de linea y fabricar entradas de log falsas.
     */
    @Test
    @DisplayName("Debe descartar un X-Origin-User con saltos de linea (log injection)")
    void shouldRejectOriginUserWithControlCharacters() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdentityFilter.ORIGIN_USER_HEADER,
                "operario\n2026-07-31 ERROR linea de log falsa");

        AtomicReference<String> originUser = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.ORIGIN_USER_MDC_KEY, originUser)));

        assertNull(originUser.get());
    }

    @Test
    @DisplayName("Debe descartar un X-Origin-User mas largo de 128 caracteres")
    void shouldRejectOversizedOriginUser() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdentityFilter.ORIGIN_USER_HEADER, "a".repeat(129));

        AtomicReference<String> originUser = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                chainCapturing(Map.of(RequestIdentityFilter.ORIGIN_USER_MDC_KEY, originUser)));

        assertNull(originUser.get());
    }

    @Test
    @DisplayName("Debe limpiar tambien originUser al terminar")
    void shouldClearOriginUserAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdentityFilter.ORIGIN_USER_HEADER, "operario.test");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(MDC.get(RequestIdentityFilter.ORIGIN_USER_MDC_KEY));
    }

    @Test
    @DisplayName("Debe correr despues de la cadena de seguridad y antes del log de acceso")
    void shouldBeOrderedBetweenSecurityAndAccessLog() {
        // La cadena de Spring Security se registra en -100: cualquier valor por
        // encima corre despues, que es lo unico que hace que este filtro tenga
        // un SecurityContext que leer.
        org.junit.jupiter.api.Assertions.assertTrue(RequestIdentityFilter.ORDER > -100);
        org.junit.jupiter.api.Assertions.assertTrue(
                RequestLoggingFilter.ORDER > RequestIdentityFilter.ORDER,
                "el log de acceso debe salir con la identidad ya puesta en el MDC");
    }
}
