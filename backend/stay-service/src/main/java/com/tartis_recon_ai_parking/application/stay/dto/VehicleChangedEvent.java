package com.tartis_recon_ai_parking.application.stay.dto;

import java.time.Instant;
import java.util.UUID;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

public record VehicleChangedEvent(
    UUID eventId,
    String type,
    String version,
    Instant occurredAt,
    VehicleChangedData data
) {
    @SuppressWarnings("java:S107")
    public record VehicleChangedData(
        UUID vehicleId,
        String plate,
        VehicleType vehicleType,
        String brand,
        String model,
        String color,
        int numDoors,
        boolean hasSidecar,
        boolean active
    ) {}
}
