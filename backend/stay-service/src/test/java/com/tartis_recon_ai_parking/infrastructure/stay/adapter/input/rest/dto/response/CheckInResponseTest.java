package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckInResponseTest {

    private final UUID stayId = UUID.randomUUID();
    private final UUID spotId = UUID.randomUUID();
    private final Instant checkIn = Instant.parse("2026-07-22T10:00:00Z");
    private final EntryTicketResponse ticket =
            new EntryTicketResponse(UUID.randomUUID(), "BARCODE-123", Instant.now());

    @Test
    @DisplayName("El constructor completo debe rellenar todos los campos")
    void allArgsConstructor() {
        CheckInResponse response = new CheckInResponse(stayId, "1234ABC", spotId, checkIn,
                StayStatus.IN_PROGRESS, ticket);

        assertThat(response.getStayId()).isEqualTo(stayId);
        assertThat(response.getPlate()).isEqualTo("1234ABC");
        assertThat(response.getSpotId()).isEqualTo(spotId);
        assertThat(response.getCheckIn()).isEqualTo(checkIn);
        assertThat(response.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
        assertThat(response.getEntryTicket()).isSameAs(ticket);
    }

    @Test
    @DisplayName("El constructor vacio y los setters deben funcionar")
    void noArgsConstructorAndSetters() {
        CheckInResponse response = new CheckInResponse();
        response.setStayId(stayId);
        response.setPlate("1234ABC");
        response.setSpotId(spotId);
        response.setCheckIn(checkIn);
        response.setStatus(StayStatus.IN_PROGRESS);
        response.setEntryTicket(ticket);

        assertThat(response.getStayId()).isEqualTo(stayId);
        assertThat(response.getPlate()).isEqualTo("1234ABC");
        assertThat(response.getSpotId()).isEqualTo(spotId);
        assertThat(response.getCheckIn()).isEqualTo(checkIn);
        assertThat(response.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
        assertThat(response.getEntryTicket()).isSameAs(ticket);
    }
}
