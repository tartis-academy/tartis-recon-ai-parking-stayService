package com.tartis_recon_ai_parking.application.stay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StayClosedEvent(
    UUID eventId,
    String eventType,
    String version,
    Instant timestamp,
    StayClosedData data
) {
    public record StayClosedData(
        UUID stayId,
        String plate,
        String spotCode,
        Instant entryDate,
        Instant exitDate,
        BigDecimal totalAmount
    ) {}

    /**
     * Factoría para construir el evento con los metadatos requeridos por el contrato (v1).
     */
    public static StayClosedEvent create(
            UUID stayId,
            String plate,
            String spotCode,
            Instant entryDate,
            Instant exitDate,
            BigDecimal totalAmount) {

        return new StayClosedEvent(
            UUID.randomUUID(),
            "StayClosedEvent",
            "v1",
            Instant.now(),
            new StayClosedData(stayId, plate, spotCode, entryDate, exitDate, totalAmount)
        );
    }
}