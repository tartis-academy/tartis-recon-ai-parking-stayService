package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckOutResponseTest {

    private final UUID stayId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final Instant checkIn = Instant.parse("2026-07-22T10:00:00Z");
    private final Instant checkOut = Instant.parse("2026-07-22T11:30:00Z");
    private final BigDecimal amount = new BigDecimal("4.50");

    @Test
    @DisplayName("El constructor completo debe rellenar todos los campos")
    void allArgsConstructor() {
        CheckOutResponse response = new CheckOutResponse(stayId, "1234ABC", checkIn, checkOut,
                90L, amount, ticketId, StayStatus.FINISHED);

        assertThat(response.getStayId()).isEqualTo(stayId);
        assertThat(response.getPlate()).isEqualTo("1234ABC");
        assertThat(response.getCheckIn()).isEqualTo(checkIn);
        assertThat(response.getCheckOut()).isEqualTo(checkOut);
        assertThat(response.getTotalMinutes()).isEqualTo(90L);
        assertThat(response.getAmount()).isEqualByComparingTo("4.50");
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getStatus()).isEqualTo(StayStatus.FINISHED);
    }

    @Test
    @DisplayName("El constructor vacio y los setters deben funcionar")
    void noArgsConstructorAndSetters() {
        CheckOutResponse response = new CheckOutResponse();
        response.setStayId(stayId);
        response.setPlate("1234ABC");
        response.setCheckIn(checkIn);
        response.setCheckOut(checkOut);
        response.setTotalMinutes(90L);
        response.setAmount(amount);
        response.setTicketId(ticketId);
        response.setStatus(StayStatus.FINISHED);

        assertThat(response.getStayId()).isEqualTo(stayId);
        assertThat(response.getPlate()).isEqualTo("1234ABC");
        assertThat(response.getCheckIn()).isEqualTo(checkIn);
        assertThat(response.getCheckOut()).isEqualTo(checkOut);
        assertThat(response.getTotalMinutes()).isEqualTo(90L);
        assertThat(response.getAmount()).isEqualByComparingTo("4.50");
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getStatus()).isEqualTo(StayStatus.FINISHED);
    }
}
