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

    @Test
    @DisplayName("Debe resolver el token Bearer desde el parametro de consulta URI ?access_token=... para soportar clientes SSE")
    void shouldResolveTokenFromQueryParameterForSse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("access_token", "my-sse-jwt-token");

        String token = resolver.resolve(request);

        assertThat(token).isEqualTo("my-sse-jwt-token");
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
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.addHeader("Authorization", "Bearer header-token");
        request.setParameter("access_token", "query-token");

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
            () -> resolver.resolve(request)
        );
    }
}
