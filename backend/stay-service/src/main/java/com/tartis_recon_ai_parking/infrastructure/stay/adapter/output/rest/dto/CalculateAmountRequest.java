package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto;

// Nombres de campo alineados con TariffPriceRequest de tariff-service
// (POST /v1/tariffs/calculate espera "type"/"minutes", no "vehicleType"/"totalMinutes").
public record CalculateAmountRequest(
        java.util.UUID tariffId,
        String type,
        Integer minutes
) {}
