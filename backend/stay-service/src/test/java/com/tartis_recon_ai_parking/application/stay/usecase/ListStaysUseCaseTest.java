package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort.VehicleInfo;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListStaysUseCaseTest {

    @Mock
    private StayPersistence stayPersistence;

    @Mock
    private StayVehiclePort vehiclePort;

    private ListStaysUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListStaysUseCase(stayPersistence, vehiclePort, new StayDTOFactory());
    }

    private Stay sampleStay(UUID stayId) {
        return Stay.restore(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(),
                UUID.randomUUID(), Instant.parse("2026-07-23T08:30:00Z"), null, null,
                StayStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Debe mapear la pagina de dominio a StayPageDTO conservando los metadatos y la matricula")
    void shouldReturnMappedPage() {
        UUID stayId = UUID.randomUUID();
        Stay stay = sampleStay(stayId);
        when(stayPersistence.findPage(StayStatus.IN_PROGRESS, 0, 20))
                .thenReturn(new StayPersistence.StayPage(List.of(stay), 0, 20, 1L, 1));
        when(vehiclePort.findById(stay.getVehicleId()))
                .thenReturn(Optional.of(new VehicleInfo(stay.getVehicleId(), "1234ABC", VehicleType.CAR, true)));

        StayPageDTO result = useCase.execute(StayStatus.IN_PROGRESS, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals(stayId, result.getContent().get(0).getStayId());
        assertEquals(StayStatus.IN_PROGRESS, result.getContent().get(0).getStatus());
        assertEquals("1234ABC", result.getContent().get(0).getPlate());
        assertEquals(0, result.getPage());
        assertEquals(20, result.getSize());
        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    @DisplayName("Debe propagar status null (sin filtro) y los parametros de paginacion al puerto")
    void shouldPassParamsThrough() {
        when(stayPersistence.findPage(null, 2, 5))
                .thenReturn(new StayPersistence.StayPage(List.of(), 2, 5, 0L, 0));

        StayPageDTO result = useCase.execute(null, 2, 5);

        assertEquals(0, result.getContent().size());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getSize());
        verify(stayPersistence).findPage(null, 2, 5);
    }

    @Test
    @DisplayName("vehicle-service caido: la matricula de esa estancia queda a null, sin romper el listado")
    void shouldKeepListing_whenVehicleServiceFailsForOneStay() {
        Stay stay = sampleStay(UUID.randomUUID());
        when(stayPersistence.findPage(null, 0, 20))
                .thenReturn(new StayPersistence.StayPage(List.of(stay), 0, 20, 1L, 1));
        when(vehiclePort.findById(any())).thenThrow(new IllegalStateException("vehicle-service caido"));

        StayPageDTO result = useCase.execute(null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertNull(result.getContent().get(0).getPlate());
    }
}
