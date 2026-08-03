package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayEntity;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayPersistenceMapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
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

    @Test
    void toEntity_ShouldReturnNull_WhenStayIsNull() {
        assertNull(mapper.toEntity(null));
    }

    // ------------------------------------------------------------------
    // Condiciones de carrera: el token de version tiene que sobrevivir al
    // viaje de ida y vuelta. Si se perdiera, el bloqueo optimista quedaria
    // desactivado sin que ningun otro test se enterase.
    // ------------------------------------------------------------------

    @Test
    void toEntity_ShouldCarryVersionBack_SoTheUpdateCanDetectConcurrentChanges() {
        Stay leidaDeBd = Stay.restore(
                UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR,
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                null, null, StayStatus.IN_PROGRESS, 7L);

        StayEntity entity = mapper.toEntity(leidaDeBd);

        assertEquals(Long.valueOf(7L), entity.getVersion(),
                "Sin la version, el UPDATE no llevaria WHERE version = ? y pisaria"
                        + " cualquier cambio concurrente");
    }

    @Test
    void toDomain_ShouldExposeVersion_SoItCanBeCarriedByTheAggregate() {
        StayEntity entity = new StayEntity(
                UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR,
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                null, null, StayStatus.IN_PROGRESS);
        // La version no va en el constructor: la pone Hibernate al leer la fila.
        entity.setVersion(3L);

        Stay stay = mapper.toDomain(entity);

        assertEquals(Long.valueOf(3L), stay.getVersion());
    }

    @Test
    void toEntity_ShouldLeaveVersionNull_ForANewStay() {
        Stay nueva = Stay.checkIn(UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR,
                UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertNull(mapper.toEntity(nueva).getVersion(),
                "Una estancia nueva debe ir sin version: es lo que hace que Spring Data"
                        + " la trate como INSERT y no como UPDATE");
    }

    @Test
    void finish_ShouldPreserveVersion_SoTheCheckOutUpdateIsGuarded() {
        Instant entrada = Instant.parse("2026-08-03T08:00:00Z");
        Stay leidaDeBd = Stay.restore(
                UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR,
                UUID.randomUUID(), UUID.randomUUID(), entrada,
                null, null, StayStatus.IN_PROGRESS, 5L);

        Stay cerrada = leidaDeBd.finish(entrada.plusSeconds(3600), new BigDecimal("4.00"));

        assertEquals(Long.valueOf(5L), cerrada.getVersion(),
                "finish() debe arrastrar la version leida: es el unico dato que"
                        + " distingue 'nadie ha tocado esto' de 'alguien se me adelanto'");
    }
}
