package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.infrastructure.config.KeycloakRoleConverter;
import com.tartis_recon_ai_parking.infrastructure.config.SecurityConfig;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.CustomizedExceptionAdapter;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventstream.SseEmitterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

// A diferencia de EventStreamRestAdapterMvcTest, aqui el registro es real.
@WebMvcTest(EventStreamRestAdapter.class)
@Import({SecurityConfig.class, CustomizedExceptionAdapter.class, SseEmitterRegistry.class})
@ActiveProfiles("test")
class EventStreamRestAdapterIntegrationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseEmitterRegistry registry;

    @Test
    @DisplayName("una conexion SSE real queda registrada como activa")
    void subscribingRegistersAConnectedClient() throws Exception {
        // Deltas, no valores absolutos: el contexto (y el registro) se cachea
        // entre tests de la clase.
        int before = registry.activeCount();

        mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).with(adminJwt()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted());

        assertEquals(before + 1, registry.activeCount());
    }

    @Test
    @DisplayName("publish() entrega el evento stay_updated al cliente suscrito")
    void publishReachesTheSubscribedClient() throws Exception {
        int before = registry.activeCount();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(SecurityConfig.SSE_PATH).with(adminJwt()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        assertEquals(before + 1, registry.activeCount());

        StayClosedEvent event = StayClosedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), "1234ABC",
                Instant.now().minusSeconds(3600), Instant.now(),
                new BigDecimal("5.00"), Instant.now());
        registry.publish(event);

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("event:stay_updated"), body);
        assertTrue(body.contains("1234ABC"), body);
        assertTrue(body.contains("id:" + event.eventId()), body);
    }

    private static JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                )
                .authorities(new KeycloakRoleConverter());
    }
}
