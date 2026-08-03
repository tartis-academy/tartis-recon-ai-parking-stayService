package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record StayCreatedEvent(
    UUID eventId,
    String type,
    String version,
    Instant occurredAt,
    StayCreatedData data
) {
    public record StayCreatedData(
        UUID stayId,
        UUID vehicleId,
        VehicleType vehicleType,
        UUID spotId,
        UUID tariffId,
        String plate,
        Instant checkIn
    ) {}

    public static StayCreatedEvent of(UUID stayId, UUID vehicleId, VehicleType vehicleType,
                                       UUID spotId, UUID tariffId, String plate,
                                       Instant checkIn, Instant occurredAt) {
        return new StayCreatedEvent(
            UUID.randomUUID(),
            "StayCreatedEvent",
            "v1",
            occurredAt,
            new StayCreatedData(stayId, vehicleId, vehicleType, spotId, tariffId, plate, checkIn)
        );
    }
}
