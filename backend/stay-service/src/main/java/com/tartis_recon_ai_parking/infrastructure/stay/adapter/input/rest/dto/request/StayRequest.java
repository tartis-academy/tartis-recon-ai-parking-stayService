package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion de check-in (POST /v1/stays/check-in).
 * Corresponde al schema {@code CheckInRequest} del openapi.yml.
 */
public class StayRequest {

    @NotBlank(message = "La matricula es obligatoria")
    public String plate;

    /** Solo necesario si el vehiculo no esta registrado (auto-registro). */
    public String vehicleType;
}
