package com.tartis_recon_ai_parking.application.stay.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la serializacion JSON de StayClosedEvent usa exactamente los
 * nombres de campo que espera spot-service (StayClosedEventData, ya mergeado
 * en release123): "type", "occurredAt" y "spotId" (UUID). Un cambio
 * accidental de nombre aqui rompe el consumo en silencio (sin este test,
 * Jackson simplemente deja el campo a null en destino).
 */
class StayClosedEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("El JSON serializado debe usar los nombres de campo del contrato real (type, occurredAt, spotId)")
    void shouldSerializeWithContractFieldNames() throws Exception {
        StayClosedEvent event = StayClosedEvent.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1234ABC",
                Instant.parse("2026-07-29T10:00:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"),
                new BigDecimal("15.50"),
                Instant.parse("2026-07-29T12:00:00Z"));

        String json = objectMapper.writeValueAsString(event);
        ObjectNode root = (ObjectNode) objectMapper.readTree(json);

        assertThat(root.has("type")).as("debe existir 'type', no 'eventType'").isTrue();
        assertThat(root.has("eventType")).as("'eventType' es el nombre antiguo, no debe existir").isFalse();

        assertThat(root.has("occurredAt")).as("debe existir 'occurredAt', no 'timestamp'").isTrue();
        assertThat(root.has("timestamp")).as("'timestamp' es el nombre antiguo, no debe existir").isFalse();

        ObjectNode data = (ObjectNode) root.get("data");
        assertThat(data.has("spotId")).as("debe existir 'data.spotId' como UUID").isTrue();
        assertThat(data.has("spotCode")).as("'spotCode' es el nombre antiguo, no debe existir").isFalse();
        assertThat(data.get("spotId").isTextual()).isTrue();

        assertThat(root.get("type").asText()).isEqualTo("StayClosedEvent");
        assertThat(root.get("version").asText()).isEqualTo("v1");
    }
}