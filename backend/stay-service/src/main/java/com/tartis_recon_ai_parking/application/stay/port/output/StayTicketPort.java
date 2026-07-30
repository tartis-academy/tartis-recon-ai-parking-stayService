package com.tartis_recon_ai_parking.application.stay.port.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface StayTicketPort {
    /**
     * Emite el ticket de entrada con código de barras en el check-in.
     */
    EntryTicketInfo issueEntryTicket(UUID stayId, String plate, Instant checkIn);

    /**
     * Genera el ticket/recibo de salida en el check-out e invalida el de entrada (IN-21).
     */
    UUID issueExitTicket(UUID stayId, UUID entryTicketId, BigDecimal totalAmount);

    record EntryTicketInfo(UUID ticketId, String barCode, Instant issuedAt) {}
}