package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;

import java.time.Instant;
import java.util.UUID;

public record CheckInResponse(
    UUID stayId,
    UUID spotId,
    Instant checkin,
    StayStatus status
) {}