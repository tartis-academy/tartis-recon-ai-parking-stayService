package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StayDTOTest {

    @Test
    @DisplayName("Debe exponer por los getters todos los valores dados en el constructor")
    void shouldExposeAllConstructorValues() {
        UUID stayId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        Instant checkIn = Instant.parse("2026-07-22T10:00:00Z");
        Instant checkOut = Instant.parse("2026-07-22T11:00:00Z");
        BigDecimal amount = new BigDecimal("3.00");

        StayDTO dto = new StayDTO(stayId, vehicleId, VehicleType.CAR, spotId, tariffId,
                checkIn, checkOut, amount, StayStatus.FINISHED);

        assertThat(dto.getStayId()).isEqualTo(stayId);
        assertThat(dto.getVehicleId()).isEqualTo(vehicleId);
        assertThat(dto.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(dto.getSpotId()).isEqualTo(spotId);
        assertThat(dto.getTariffId()).isEqualTo(tariffId);
        assertThat(dto.getCheckIn()).isEqualTo(checkIn);
        assertThat(dto.getCheckOut()).isEqualTo(checkOut);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("3.00");
        assertThat(dto.getStatus()).isEqualTo(StayStatus.FINISHED);
    }

    @Test
    @DisplayName("Debe admitir checkOut y totalAmount nulos (estancia en curso)")
    void shouldAllowNullCheckOutAndAmount() {
        StayDTO dto = new StayDTO(UUID.randomUUID(), UUID.randomUUID(), VehicleType.MOTORBIKE,
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS);

        assertThat(dto.getCheckOut()).isNull();
        assertThat(dto.getTotalAmount()).isNull();
        assertThat(dto.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
    }
}
