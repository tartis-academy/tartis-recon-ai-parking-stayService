package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
    @NotBlank(message = "La matrícula es obligatoria")
    String plate,

    @NotNull(message = "El tipo de vehículo es obligatorio")
    VehicleType vehicleType
) {}