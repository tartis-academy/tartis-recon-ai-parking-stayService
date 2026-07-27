package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntryTicketResponseTest {

    private final UUID ticketId = UUID.randomUUID();
    private final Instant issuedAt = Instant.parse("2026-07-22T10:00:00Z");

    @Test
    @DisplayName("El constructor completo debe rellenar todos los campos")
    void allArgsConstructor() {
        EntryTicketResponse ticket = new EntryTicketResponse(ticketId, "BARCODE-123", issuedAt);

        assertThat(ticket.getTicketId()).isEqualTo(ticketId);
        assertThat(ticket.getBarCode()).isEqualTo("BARCODE-123");
        assertThat(ticket.getIssuedAt()).isEqualTo(issuedAt);
    }

    @Test
    @DisplayName("El constructor vacio y los setters deben funcionar")
    void noArgsConstructorAndSetters() {
        EntryTicketResponse ticket = new EntryTicketResponse();
        ticket.setTicketId(ticketId);
        ticket.setBarCode("BARCODE-123");
        ticket.setIssuedAt(issuedAt);

        assertThat(ticket.getTicketId()).isEqualTo(ticketId);
        assertThat(ticket.getBarCode()).isEqualTo("BARCODE-123");
        assertThat(ticket.getIssuedAt()).isEqualTo(issuedAt);
    }
}
