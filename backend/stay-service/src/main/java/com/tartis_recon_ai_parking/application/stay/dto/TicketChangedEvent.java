package com.tartis_recon_ai_parking.application.stay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TicketChangedEvent(
    UUID eventId,
    String type,
    String version,
    Instant occurredAt,
    TicketChangedData data
) {
    public record TicketChangedData(
        UUID ticketId,
        UUID stayId,
        String code,
        Instant issuedAt,
        String status,
        BigDecimal amount
    ) {}
}
