package com.tartis_recon_ai_parking.application.stay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StayClosedEvent(
    UUID eventId,
    String type,
    String version,
    Instant occurredAt,
    StayClosedData data
) {
    public record StayClosedData(
        UUID stayId,
        UUID spotId,
        String plate,
        Instant entryDate,
        Instant exitDate,
        BigDecimal totalAmount
    ) {}

    public static StayClosedEvent of(UUID stayId, UUID spotId, String plate,
                                      Instant entryDate, Instant exitDate,
                                      BigDecimal totalAmount, Instant occurredAt) {
        return new StayClosedEvent(
            UUID.randomUUID(),
            "StayClosedEvent",
            "v1",
            occurredAt,
            new StayClosedData(stayId, spotId, plate, entryDate, exitDate, totalAmount)
        );
    }
}