package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final BearerTokenResolver resolver = securityConfig.bearerTokenResolver();

    @Test
    @DisplayName("Debe resolver el token Bearer desde la cabecera Authorization")
    void shouldResolveTokenFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer my-jwt-token");

        String token = resolver.resolve(request);

        assertThat(token).isEqualTo("my-jwt-token");
    }

    /**
     * Helper: una peticion tal y como la manda EventSource, que no puede poner
     * cabeceras y por eso lleva el token en la URL.
     */
    private static MockHttpServletRequest peticionSse(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SecurityConfig.SSE_PATH);
        request.setRequestURI(SecurityConfig.SSE_PATH);
        request.setParameter("access_token", token);
        return request;
    }

    @Test
    @DisplayName("Debe resolver el token del query param access_token en la ruta SSE")
    void shouldResolveTokenFromQueryParameterOnSseRoute() {
        String token = resolver.resolve(peticionSse("my-sse-jwt-token"));

        assertThat(token).isEqualTo("my-sse-jwt-token");
    }

    /**
     * SSE-08: {@code access_token} es el unico nombre aceptado. {@code jwt}, el
     * default del plugin de Kong, no vale: la route del SSE en {@code kong.yml}
     * declara {@code uri_param_names: ["access_token"]} y el front manda ese
     * mismo nombre, asi que un segundo alias solo anadiria superficie.
     */
    @Test
    @DisplayName("Debe IGNORAR el query param jwt en la ruta SSE")
    void shouldIgnoreJwtQueryParameterOnSseRoute() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SecurityConfig.SSE_PATH);
        request.setRequestURI(SecurityConfig.SSE_PATH);
        request.setParameter("jwt", "my-jwt-query-token");

        assertThat(resolver.resolve(request)).isNull();
    }


    /**
     * Este es el test que de verdad protege el cambio de GW-06/SSE-08: antes
     * {@code setAllowUriQueryParameter(true)} estaba puesto de forma global y
     * CUALQUIER endpoint aceptaba el token por URL, multiplicando la superficie
     * de fuga (historial del navegador, Referer, logs de proxies) sin ninguna
     * necesidad, porque el resto de rutas las consume fetch(), que si puede
     * mandar cabeceras. Si alguien vuelve a ponerlo global, esto salta.
     */
    @Test
    @DisplayName("Debe IGNORAR el token por query string fuera de la ruta SSE")
    void shouldIgnoreQueryParameterOutsideSseRoute() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/stays/check-in");
        request.setRequestURI("/v1/stays/check-in");
        request.setParameter("access_token", "token-por-la-url");

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    @DisplayName("Debe ignorar el query param incluso en la ruta SSE si el metodo no es GET")
    void shouldIgnoreQueryParameterOnSseRouteForNonGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", SecurityConfig.SSE_PATH);
        request.setRequestURI(SecurityConfig.SSE_PATH);
        request.setParameter("access_token", "token-por-la-url");

        // EventSource solo hace GET: restringir el metodo reduce la superficie
        // sin coste ninguno.
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    @DisplayName("Debe seguir aceptando la cabecera Authorization en la ruta SSE")
    void shouldStillAcceptHeaderOnSseRoute() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SecurityConfig.SSE_PATH);
        request.setRequestURI(SecurityConfig.SSE_PATH);
        request.addHeader("Authorization", "Bearer token-por-cabecera");

        assertThat(resolver.resolve(request)).isEqualTo("token-por-cabecera");
    }

    @Test
    @DisplayName("Debe devolver null si no se envia token en cabecera ni en parametro de consulta")
    void shouldReturnNullWhenNoTokenProvided() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String token = resolver.resolve(request);

        assertThat(token).isNull();
    }

    // --- Casos infelices ---

    @Test
    @DisplayName("Debe devolver null si la cabecera usa un esquema distinto de Bearer (ej: Basic)")
    void shouldReturnNullWhenAuthorizationSchemeIsNotBearer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        String token = resolver.resolve(request);

        assertThat(token).isNull();
    }

    @Test
    @DisplayName("Debe lanzar OAuth2AuthenticationException cuando el token llega por cabecera y por query param a la vez (prevencion de token smuggling)")
    void shouldThrowWhenTokenPresentInBothHeaderAndQueryParam() {
        // Solo aplica en la ruta SSE: es la unica donde se mira el query param.
        // Fuera de ella la cabecera manda y el parametro se ignora sin mas.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SecurityConfig.SSE_PATH);
        request.setRequestURI(SecurityConfig.SSE_PATH);
        request.addHeader("Authorization", "Bearer header-token");
        request.setParameter("access_token", "query-token");

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
            () -> resolver.resolve(request)
        );
    }

    @Test
    @DisplayName("Debe lanzar OAuth2AuthenticationException cuando se envian multiples parametros access_token")
    void shouldThrowWhenMultipleAccessTokenParametersPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SecurityConfig.SSE_PATH);
        request.setRequestURI(SecurityConfig.SSE_PATH);
        request.addParameter("access_token", "token-1");
        request.addParameter("access_token", "token-2");

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
            () -> resolver.resolve(request)
        );
    }
}

