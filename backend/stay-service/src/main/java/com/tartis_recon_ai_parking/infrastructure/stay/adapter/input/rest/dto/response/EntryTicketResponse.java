package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Ticket de entrada emitido en el check-in.
 * Corresponde al schema {@code EntryTicket} del openapi.yml.
 */
public class EntryTicketResponse {

    private UUID ticketId;
    private String barCode;
    private Instant issuedAt;

    public EntryTicketResponse() {
    }

    public EntryTicketResponse(UUID ticketId, String barCode, Instant issuedAt) {
        this.ticketId = ticketId;
        this.barCode = barCode;
        this.issuedAt = issuedAt;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public String getBarCode() {
        return barCode;
    }

    public void setBarCode(String barCode) {
        this.barCode = barCode;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }
}
