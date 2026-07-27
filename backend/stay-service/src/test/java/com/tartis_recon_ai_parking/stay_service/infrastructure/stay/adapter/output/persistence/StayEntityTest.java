package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayEntity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Entidad JPA sin logica propia: solo verifica que cada setter deja el valor
 * accesible por su getter (JaCoCo no marca setters como cubiertos por el uso
 * indirecto via constructor/mapper).
 */
class StayEntityTest {

    @Test
    void settersShouldUpdateFieldsExposedByGetters() {
        StayEntity entity = new StayEntity(
                UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(),
                UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS);

        UUID uniqueId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        Instant checkIn = Instant.parse("2026-07-23T08:30:00Z");
        Instant checkOut = Instant.parse("2026-07-23T10:00:00Z");
        BigDecimal totalAmount = new BigDecimal("3.00");

        entity.setUniqueId(uniqueId);
        entity.setVehicleId(vehicleId);
        entity.setVehicleType(VehicleType.MOTORBIKE);
        entity.setSpotId(spotId);
        entity.setTariffId(tariffId);
        entity.setCheckIn(checkIn);
        entity.setCheckOut(checkOut);
        entity.setTotalAmount(totalAmount);
        entity.setStatus(StayStatus.FINISHED);

        assertEquals(uniqueId, entity.getUniqueId());
        assertEquals(vehicleId, entity.getVehicleId());
        assertEquals(VehicleType.MOTORBIKE, entity.getVehicleType());
        assertEquals(spotId, entity.getSpotId());
        assertEquals(tariffId, entity.getTariffId());
        assertEquals(checkIn, entity.getCheckIn());
        assertEquals(checkOut, entity.getCheckOut());
        assertEquals(0, totalAmount.compareTo(entity.getTotalAmount()));
        assertEquals(StayStatus.FINISHED, entity.getStatus());
    }
}
