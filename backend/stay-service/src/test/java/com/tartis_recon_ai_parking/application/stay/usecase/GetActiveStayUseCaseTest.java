package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveStayUseCaseTest {

    @Mock
    private StayPersistence stayPersistence;

    private StayDTOFactory stayDTOFactory;
    private GetActiveStayUseCase getActiveStayUseCase;

    @BeforeEach
    void setUp() {
        stayDTOFactory = new StayDTOFactory();
        getActiveStayUseCase = new GetActiveStayUseCase(stayPersistence, stayDTOFactory);
    }

    @Test
    void shouldReturnActiveStay_whenFoundByVehicleId() {
        UUID vehicleId = UUID.randomUUID();
        UUID stayId = UUID.randomUUID();
        Stay stay = Stay.checkIn(stayId, vehicleId, VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        when(stayPersistence.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS)).thenReturn(Optional.of(stay));

        StayDTO result = getActiveStayUseCase.execute(vehicleId);

        assertNotNull(result);
        assertEquals(stayId, result.getStayId());
        assertEquals(vehicleId, result.getVehicleId());
        assertEquals(StayStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void shouldReturnActiveStay_whenFoundByStayId() {
        UUID stayId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Stay stay = Stay.checkIn(stayId, vehicleId, VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        when(stayPersistence.findByVehicleIdAndStatus(stayId, StayStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(stayPersistence.findById(stayId)).thenReturn(Optional.of(stay));

        StayDTO result = getActiveStayUseCase.execute(stayId);

        assertNotNull(result);
        assertEquals(stayId, result.getStayId());
        assertEquals(StayStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void shouldThrowStayNotFoundException_whenNotFoundByVehicleIdOrStayId() {
        UUID id = UUID.randomUUID();
        when(stayPersistence.findByVehicleIdAndStatus(id, StayStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(stayPersistence.findById(id)).thenReturn(Optional.empty());

        assertThrows(StayNotFoundException.class, () -> getActiveStayUseCase.execute(id));
    }

    @Test
    void shouldThrowStayNotFoundException_whenFoundStayIsNotInProgress() {
        UUID stayId = UUID.randomUUID();
        Stay finishedStay = Stay.restore(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(), java.math.BigDecimal.TEN, StayStatus.FINISHED);

        when(stayPersistence.findByVehicleIdAndStatus(stayId, StayStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(stayPersistence.findById(stayId)).thenReturn(Optional.of(finishedStay));

        assertThrows(StayNotFoundException.class, () -> getActiveStayUseCase.execute(stayId));
    }
}
