package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayEntity;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayPersistenceAdapter;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayPersistenceMapper;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StayPersistenceAdapterTest {

    @Mock
    private StayRepository repository;

    @Mock
    private StayPersistenceMapper mapper;

    @InjectMocks
    private StayPersistenceAdapter adapter;

    private Stay stayDomain;
    private StayEntity stayEntity;
    private UUID stayId;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        stayDomain = Stay.checkIn(
                stayId,
                vehicleId,
                VehicleType.CAR,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now()
        );

        stayEntity = new StayEntity(
                stayId,
                vehicleId,
                VehicleType.CAR,
                stayDomain.getSpotId(),
                stayDomain.getTariffId(),
                stayDomain.getCheckIn(),
                null,
                null,
                StayStatus.IN_PROGRESS
        );
    }

    @Test
    void save_ShouldPersistAndReturnStay() {
        when(mapper.toEntity(stayDomain)).thenReturn(stayEntity);
        when(repository.save(stayEntity)).thenReturn(stayEntity);
        when(mapper.toDomain(stayEntity)).thenReturn(stayDomain);

        Stay result = adapter.save(stayDomain);

        assertNotNull(result);
        assertEquals(stayId, result.getId());
        verify(repository).save(stayEntity);
    }

    @Test
    void findById_ShouldReturnStay_WhenFound() {
        when(repository.findById(stayId)).thenReturn(Optional.of(stayEntity));
        when(mapper.toDomain(stayEntity)).thenReturn(stayDomain);

        Optional<Stay> result = adapter.findById(stayId);

        assertTrue(result.isPresent());
        assertEquals(stayId, result.get().getId());
    }

    @Test
    void findAll_ShouldReturnListOfStays() {
        when(repository.findAll()).thenReturn(List.of(stayEntity));
        when(mapper.toDomain(stayEntity)).thenReturn(stayDomain);

        List<Stay> result = adapter.findAll();

        assertEquals(1, result.size());
        assertEquals(stayId, result.get(0).getId());
    }

    @Test
    void existsByVehicleIdAndStatus_ShouldReturnTrue_WhenExists() {
        when(repository.existsByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS)).thenReturn(true);

        boolean exists = adapter.existsByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS);

        assertTrue(exists);
        verify(repository).existsByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS);
    }

    @Test
    void findByVehicleIdAndStatus_ShouldReturnStay_WhenFound() {
        when(repository.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS)).thenReturn(Optional.of(stayEntity));
        when(mapper.toDomain(stayEntity)).thenReturn(stayDomain);

        Optional<Stay> result = adapter.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS);

        assertTrue(result.isPresent());
        assertEquals(vehicleId, result.get().getVehicleId());
    }
}
