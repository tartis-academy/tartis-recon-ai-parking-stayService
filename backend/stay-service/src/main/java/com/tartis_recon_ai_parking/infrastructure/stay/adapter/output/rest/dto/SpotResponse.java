package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotResponse(
        UUID id,
        String status,
        String type
) {}