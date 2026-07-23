package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StayCheckOutRequestTest {

    @Test
    @DisplayName("Debe almacenar y devolver los campos plate y entryTicketId")
    void shouldHoldFields() {
        UUID ticketId = UUID.randomUUID();
        StayCheckOutRequest request = new StayCheckOutRequest();
        request.plate = "1234ABC";
        request.entryTicketId = ticketId;

        assertThat(request.plate).isEqualTo("1234ABC");
        assertThat(request.entryTicketId).isEqualTo(ticketId);
    }
}
