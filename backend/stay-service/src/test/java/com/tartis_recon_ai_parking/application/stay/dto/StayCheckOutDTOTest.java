package com.tartis_recon_ai_parking.application.stay.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StayCheckOutDTOTest {

    @Test
    @DisplayName("Debe exponer plate y entryTicketId dados en el constructor")
    void shouldExposeConstructorValues() {
        UUID ticketId = UUID.randomUUID();
        StayCheckOutDTO dto = new StayCheckOutDTO("1234ABC", ticketId);

        assertThat(dto.getPlate()).isEqualTo("1234ABC");
        assertThat(dto.getEntryTicketId()).isEqualTo(ticketId);
    }

    @Test
    @DisplayName("Debe admitir uno de los dos campos nulo (plate o entryTicketId)")
    void shouldAllowEitherFieldNull() {
        StayCheckOutDTO byPlate = new StayCheckOutDTO("1234ABC", null);
        StayCheckOutDTO byTicket = new StayCheckOutDTO(null, UUID.randomUUID());

        assertThat(byPlate.getEntryTicketId()).isNull();
        assertThat(byTicket.getPlate()).isNull();
    }
}
