package com.tartis_recon_ai_parking.application.stay.port.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface StayTicketPort {
    /**
     * Emite el ticket de entrada con código de barras en el check-in.
     */
    EntryTicketInfo issueEntryTicket(UUID stayId, String plate, Instant checkIn);


    record EntryTicketInfo(UUID ticketId, String barCode, Instant issuedAt) {}
}