package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventpublisher.event;

import java.time.Instant;
import java.util.UUID;

public record EntryTicketOfflineEvent(
        UUID stayId,
        String plate,
        String barCode,
        Instant issuedAt
) {}