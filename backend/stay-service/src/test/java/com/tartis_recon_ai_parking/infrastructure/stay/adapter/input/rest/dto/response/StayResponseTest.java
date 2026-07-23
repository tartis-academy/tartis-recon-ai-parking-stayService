package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StayResponseTest {

    private final UUID stayId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final UUID spotId = UUID.randomUUID();
    private final UUID tariffId = UUID.randomUUID();
    private final Instant checkIn = Instant.parse("2026-07-22T10:00:00Z");
    private final Instant checkOut = Instant.parse("2026-07-22T11:00:00Z");
    private final BigDecimal amount = new BigDecimal("3.00");

    @Test
    @DisplayName("El constructor completo debe rellenar todos los campos")
    void allArgsConstructor() {
        StayResponse response = new StayResponse(stayId, "1234ABC", vehicleId, spotId, tariffId,
                StayStatus.FINISHED, checkIn, checkOut, amount);

        assertThat(response.getStayId()).isEqualTo(stayId);
        assertThat(response.getPlate()).isEqualTo("1234ABC");
        assertThat(response.getVehicleId()).isEqualTo(vehicleId);
        assertThat(response.getSpotId()).isEqualTo(spotId);
        assertThat(response.getTariffId()).isEqualTo(tariffId);
        assertThat(response.getStatus()).isEqualTo(StayStatus.FINISHED);
        assertThat(response.getCheckIn()).isEqualTo(checkIn);
        assertThat(response.getCheckOut()).isEqualTo(checkOut);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("El constructor vacio y los setters deben funcionar")
    void noArgsConstructorAndSetters() {
        StayResponse response = new StayResponse();
        response.setStayId(stayId);
        response.setPlate("1234ABC");
        response.setVehicleId(vehicleId);
        response.setSpotId(spotId);
        response.setTariffId(tariffId);
        response.setStatus(StayStatus.IN_PROGRESS);
        response.setCheckIn(checkIn);
        response.setCheckOut(null);
        response.setTotalAmount(null);

        assertThat(response.getStayId()).isEqualTo(stayId);
        assertThat(response.getPlate()).isEqualTo("1234ABC");
        assertThat(response.getVehicleId()).isEqualTo(vehicleId);
        assertThat(response.getSpotId()).isEqualTo(spotId);
        assertThat(response.getTariffId()).isEqualTo(tariffId);
        assertThat(response.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
        assertThat(response.getCheckIn()).isEqualTo(checkIn);
        assertThat(response.getCheckOut()).isNull();
        assertThat(response.getTotalAmount()).isNull();
    }
}
