package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

// tariff-service (PriceResponse) solo devuelve "price", no tariffId.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CalculateAmountResponse(
        BigDecimal price
) {}
