package com.tartis_recon_ai_parking.application.stay.factory;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StayDTOFactoryTest {

    private final StayDTOFactory factory = new StayDTOFactory();

    private static final UUID ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID SPOT_ID = UUID.randomUUID();
    private static final UUID TARIFF_ID = UUID.randomUUID();
    private static final Instant CHECK_IN = Instant.parse("2026-07-22T10:00:00Z");

    @Test
    @DisplayName("create(Stay) debe copiar todos los campos del dominio al DTO")
    void createFromDomainShouldMapAllFields() {
        Instant checkOut = CHECK_IN.plus(1, ChronoUnit.HOURS);
        Stay stay = Stay.restore(ID, VEHICLE_ID, VehicleType.CAR, SPOT_ID, TARIFF_ID,
                CHECK_IN, checkOut, new BigDecimal("3.00"), StayStatus.FINISHED);

        StayDTO dto = factory.create(stay);

        assertThat(dto.getStayId()).isEqualTo(ID);
        assertThat(dto.getVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(dto.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(dto.getSpotId()).isEqualTo(SPOT_ID);
        assertThat(dto.getTariffId()).isEqualTo(TARIFF_ID);
        assertThat(dto.getCheckIn()).isEqualTo(CHECK_IN);
        assertThat(dto.getCheckOut()).isEqualTo(checkOut);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("3.00");
        assertThat(dto.getStatus()).isEqualTo(StayStatus.FINISHED);
    }

    @Test
    @DisplayName("create(Stay) de una estancia en curso deja checkOut y totalAmount nulos")
    void createFromInProgressStay() {
        Stay stay = Stay.checkIn(ID, VEHICLE_ID, VehicleType.MOTORBIKE, SPOT_ID, TARIFF_ID, CHECK_IN);

        StayDTO dto = factory.create(stay);

        assertThat(dto.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
        assertThat(dto.getCheckOut()).isNull();
        assertThat(dto.getTotalAmount()).isNull();
    }

    @Test
    @DisplayName("create(List<Stay>) debe mapear cada estancia y conservar el tamaño")
    void createFromListShouldMapEach() {
        Stay a = Stay.checkIn(ID, VEHICLE_ID, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Stay b = Stay.checkIn(UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR_PMR,
                UUID.randomUUID(), UUID.randomUUID(), CHECK_IN);

        List<StayDTO> dtos = factory.create(List.of(a, b));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getStayId()).isEqualTo(a.getId());
        assertThat(dtos.get(1).getStayId()).isEqualTo(b.getId());
    }

    @Test
    @DisplayName("create(List<Stay>) vacia debe devolver una lista vacia")
    void createFromEmptyList() {
        assertThat(factory.create(List.<Stay>of())).isEmpty();
    }
}
