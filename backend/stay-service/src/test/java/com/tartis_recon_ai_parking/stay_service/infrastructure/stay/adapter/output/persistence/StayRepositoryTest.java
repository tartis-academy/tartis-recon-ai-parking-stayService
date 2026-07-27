package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayEntity;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class StayRepositoryTest {

    @Autowired
    private StayRepository repository;

    @Test
    void existsByVehicleIdAndStatus_ShouldReturnTrue_WhenRecordExists() {
        // Arrange
        UUID vehicleId = UUID.randomUUID();
        StayEntity entity = new StayEntity(
                UUID.randomUUID(),
                vehicleId,
                VehicleType.CAR,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                StayStatus.IN_PROGRESS
        );
        repository.save(entity);

        // Act & Assert
        boolean exists = repository.existsByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS);
        assertTrue(exists);

        boolean notExists = repository.existsByVehicleIdAndStatus(vehicleId, StayStatus.FINISHED);
        assertFalse(notExists);
    }

    @Test
    void findByVehicleIdAndStatus_ShouldReturnEntity_WhenRecordExists() {
        // Arrange
        UUID vehicleId = UUID.randomUUID();
        StayEntity entity = new StayEntity(
                UUID.randomUUID(),
                vehicleId,
                VehicleType.CAR,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                StayStatus.IN_PROGRESS
        );
        repository.save(entity);

        // Act
        Optional<StayEntity> found = repository.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(vehicleId, found.get().getVehicleId());
    }
}
