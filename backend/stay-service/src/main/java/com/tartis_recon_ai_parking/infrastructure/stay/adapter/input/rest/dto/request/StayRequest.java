package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public class StayRequest {

    @NotBlank(message = "La matricula es obligatoria")
    public String plate;

    public String vehicleType;

    // Solo se usan si la matricula no esta registrada todavia.
    public String brand;
    public String model;
    public String color;
}
