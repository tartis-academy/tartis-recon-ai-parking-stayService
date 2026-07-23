package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleResponse(
        UUID uniqueId,
        String type,
        String plate,
        Boolean active
) {
    // Getter defensivo para evitar NPE si 'active' viene null
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}