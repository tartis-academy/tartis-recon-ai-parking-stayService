package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class StayCheckOutRequest {

    @NotBlank(message = "La matricula es obligatoria")
    public String plate;

    public UUID entryTicketId;
}
