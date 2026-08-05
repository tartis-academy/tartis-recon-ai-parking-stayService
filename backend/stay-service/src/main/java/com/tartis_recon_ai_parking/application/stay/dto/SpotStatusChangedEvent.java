package com.tartis_recon_ai_parking.application.stay.dto;

import java.time.Instant;
import java.util.UUID;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

public record SpotStatusChangedEvent(
    UUID eventId,
    String type,
    String version,
    Instant occurredAt,
    SpotStatusChangedData data
) {
    public record SpotStatusChangedData(
        UUID spotId,
        VehicleType vehicleType,
        String status
    ) {}
}
