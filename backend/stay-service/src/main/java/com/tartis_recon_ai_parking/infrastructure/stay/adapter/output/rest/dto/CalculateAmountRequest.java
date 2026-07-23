package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto;

public record CalculateAmountRequest(
        String vehicleType,
        long totalMinutes
) {}
