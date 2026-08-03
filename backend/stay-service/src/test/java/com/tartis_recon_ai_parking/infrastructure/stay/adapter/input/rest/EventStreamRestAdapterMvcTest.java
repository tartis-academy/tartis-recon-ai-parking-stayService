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
