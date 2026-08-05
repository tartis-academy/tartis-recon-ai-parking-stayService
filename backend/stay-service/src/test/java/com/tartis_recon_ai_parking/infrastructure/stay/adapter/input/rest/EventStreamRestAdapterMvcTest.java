package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.infrastructure.config.KeycloakRoleConverter;
import com.tartis_recon_ai_parking.infrastructure.config.SecurityConfig;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.CustomizedExceptionAdapter;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventstream.SseEmitterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(EventStreamRestAdapter.class)
@Import({SecurityConfig.class, CustomizedExceptionAdapter.class})
@ActiveProfiles("test")
class EventStreamRestAdapterMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseEmitterRegistry registry;

    @MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Debe rechazar con 401 una peticion sin token")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        verify(registry, never()).subscribe();
    }

    @Test
    @DisplayName("USER: debe denegar la suscripcion al stream (403)")
    void shouldDenyEventsForUser() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).with(userJwt()))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        verify(registry, never()).subscribe();
    }

    @Test
    @DisplayName("ADMIN: debe abrir el stream SSE (asincrono, text/event-stream)")
    void shouldAllowEventsForAdmin() throws Exception {
        when(registry.subscribe()).thenReturn(new SseEmitter());

        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).with(adminJwt())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andExpect(MockMvcResultMatchers.status().isOk());

        verify(registry).subscribe();
    }

    @Test
    @DisplayName("OPERARIO: debe abrir el stream SSE (asincrono, text/event-stream)")
    void shouldAllowEventsForOperario() throws Exception {
        when(registry.subscribe()).thenReturn(new SseEmitter());

        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).with(operarioJwt())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andExpect(MockMvcResultMatchers.status().isOk());

        verify(registry).subscribe();
    }

    // Unico test que recorre la cadena real (resolver -> decoder -> @PreAuthorize):
    // los demas happy path usan post-processors, que inyectan la Authentication y
    // se saltan el bearerTokenResolver.
    @Test
    @DisplayName("Debe abrir el stream con un token valido por query param ?access_token")
    void shouldAllowSubscriptionWithTokenInAccessTokenQueryParam() throws Exception {
        when(registry.subscribe()).thenReturn(new SseEmitter());

        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                .withTokenValue("valid-access-token")
                .header("alg", "none")
                .claim("sub", UUID.randomUUID().toString())
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        when(jwtDecoder.decode("valid-access-token")).thenReturn(jwt);

        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH)
                        .param("access_token", "valid-access-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andExpect(MockMvcResultMatchers.status().isOk());

        verify(registry).subscribe();
    }

    @Test
    @DisplayName("Debe rechazar con 401 una peticion con token invalido por query param ?access_token")
    void shouldReturn401WhenInvalidTokenInQueryParam() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                        org.springframework.security.oauth2.server.resource.BearerTokenErrors
                                .invalidToken("Invalid token")));

        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).param("access_token", "invalid-token"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        verify(registry, never()).subscribe();
    }

    // SSE-08: ?jwt= (el default del plugin de Kong) no es un nombre aceptado, asi
    // que la peticion llega sin token y cae en 401 como cualquier anonima.
    @Test
    @DisplayName("Debe rechazar con 401 una peticion con el token en el query param ?jwt")
    void shouldReturn401WhenTokenIsInJwtQueryParam() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).param("jwt", "cualquier-token"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        verify(registry, never()).subscribe();
    }

    @Test
    @DisplayName("Debe rechazar con 401 cuando se envian multiples parametros access_token")
    void shouldReturn401WhenMultipleAccessTokenQueryParameters() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH)
                        .param("access_token", "token-1")
                        .param("access_token", "token-2"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        verify(registry, never()).subscribe();
    }

    @Test
    @DisplayName("Debe rechazar con 401 cuando el token llega por cabecera y por query a la vez (token smuggling)")
    void shouldReturn401WhenTokenInBothHeaderAndQueryParam() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH)
                        .header("Authorization", "Bearer header-token")
                        .param("access_token", "query-token"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        verify(registry, never()).subscribe();
    }


    private static JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                )
                .authorities(new KeycloakRoleConverter());
    }

    private static JwtRequestPostProcessor operarioJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("OPERARIO")))
                )
                .authorities(new KeycloakRoleConverter());
    }

    private static JwtRequestPostProcessor userJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                )
                .authorities(new KeycloakRoleConverter());
    }
}
