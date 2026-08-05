package com.tartis_recon_ai_parking.application.stay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

public record TariffChangedEvent(
    UUID eventId,
    String type,
    String version,
    Instant occurredAt,
    TariffChangedData data
) {
    public record TariffChangedData(
        UUID tariffId,
        String name,
        VehicleType vehicleType,
        BigDecimal pricePerMinute,
        BigDecimal basePrice,
        boolean active
    ) {}
}
