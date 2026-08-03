package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.infrastructure.config.KeycloakRoleConverter;
import com.tartis_recon_ai_parking.infrastructure.config.SecurityConfig;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.CustomizedExceptionAdapter;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventstream.SseEmitterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

// A diferencia de EventStreamRestAdapterMvcTest, aqui el registro es real.
@WebMvcTest(EventStreamRestAdapter.class)
@Import({SecurityConfig.class, CustomizedExceptionAdapter.class, SseEmitterRegistry.class})
@ActiveProfiles("test")
class EventStreamRestAdapterIntegrationMvcTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseEmitterRegistry registry;

    @Test
    @DisplayName("una conexion SSE real queda registrada como activa")
    void subscribingRegistersAConnectedClient() throws Exception {
        assertEquals(0, registry.activeCount());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/events").with(adminJwt()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted());

        assertEquals(1, registry.activeCount());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                )
                .authorities(new KeycloakRoleConverter());
    }
}
