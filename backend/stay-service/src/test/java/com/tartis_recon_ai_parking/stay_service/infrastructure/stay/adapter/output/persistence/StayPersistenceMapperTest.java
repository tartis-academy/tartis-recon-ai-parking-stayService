package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayEntity;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayPersistenceMapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StayPersistenceMapperTest {

    private final StayPersistenceMapper mapper = Mappers.getMapper(StayPersistenceMapper.class);

    @Test
    void toEntity_ShouldMapDomainToEntityCorrectly() {
        UUID id = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Stay stay = Stay.checkIn(id, vehicleId, VehicleType.MOTORBIKE, UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        StayEntity entity = mapper.toEntity(stay);

        assertNotNull(entity);
        assertEquals(id, entity.getUniqueId());
        assertEquals(vehicleId, entity.getVehicleId());
        assertEquals(VehicleType.MOTORBIKE, entity.getVehicleType());
    }

    @Test
    void toDomain_ShouldMapEntityToDomainCorrectly() {
        UUID id = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Instant now = Instant.now();
        StayEntity entity = new StayEntity(
                id,
                vehicleId,
                VehicleType.MOTORBIKE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now,
                null,
                null,
                StayStatus.IN_PROGRESS
        );

        Stay stay = mapper.toDomain(entity);

        assertNotNull(stay);
        assertEquals(id, stay.getId());
        assertEquals(vehicleId, stay.getVehicleId());
        assertEquals(StayStatus.IN_PROGRESS, stay.getStatus());
    }

    @Test
    void toDomain_ShouldReturnNull_WhenEntityIsNull() {
        assertNull(mapper.toDomain(null));
    }
}
