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
    void existsByPlateAndStatus_ShouldReturnTrue_WhenRecordExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        StayEntity entity = new StayEntity(
                id,
                "7777CCC",
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
        boolean exists = repository.existsByPlateAndStatus("7777CCC", StayStatus.IN_PROGRESS);
        assertTrue(exists);

        boolean notExists = repository.existsByPlateAndStatus("7777CCC", StayStatus.FINISHED);
        assertFalse(notExists);
    }

    @Test
    void findByPlateAndStatus_ShouldReturnEntity_WhenRecordExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        StayEntity entity = new StayEntity(
                id,
                "8888DDD",
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
        Optional<StayEntity> found = repository.findByPlateAndStatus("8888DDD", StayStatus.IN_PROGRESS);

        // Assert
        assertTrue(found.isPresent());
        assertEquals("8888DDD", found.get().getPlate());
    }
}