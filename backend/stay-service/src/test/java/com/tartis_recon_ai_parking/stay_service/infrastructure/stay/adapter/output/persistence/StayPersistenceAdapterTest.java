package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    @Test
    void findPage_WithStatus_ShouldQueryByStatusAndMapPage() {
        when(repository.findByStatus(eq(StayStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stayEntity), PageRequest.of(0, 20), 1));
        when(mapper.toDomain(stayEntity)).thenReturn(stayDomain);

        StayPersistence.StayPage result = adapter.findPage(StayStatus.IN_PROGRESS, 0, 20);

        assertEquals(1, result.content().size());
        assertEquals(stayId, result.content().get(0).getId());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.totalPages());
        verify(repository).findByStatus(eq(StayStatus.IN_PROGRESS), any(Pageable.class));
    }

    @Test
    void findPage_WithoutStatus_ShouldUseFindAll() {
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stayEntity), PageRequest.of(0, 20), 1));
        when(mapper.toDomain(stayEntity)).thenReturn(stayDomain);

        StayPersistence.StayPage result = adapter.findPage(null, 0, 20);

        assertEquals(1, result.content().size());
        verify(repository).findAll(any(Pageable.class));
    }
}
