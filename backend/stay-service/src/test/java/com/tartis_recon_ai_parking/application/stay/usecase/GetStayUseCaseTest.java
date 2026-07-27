package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** Consulta de una estancia por id (HU-08): camino feliz y estancia inexistente. */
@ExtendWith(MockitoExtension.class)
class GetStayUseCaseTest {

    @Mock
    private StayPersistence stayPersistence;

    private final StayDTOFactory stayDTOFactory = new StayDTOFactory();

    @Test
    @DisplayName("estancia existente -> se devuelve su detalle")
    void execute_returnsStay() {
        GetStayUseCase useCase = new GetStayUseCase(stayPersistence, stayDTOFactory);
        Stay stay = Stay.checkIn(UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR,
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-07-23T08:30:00Z"));
        when(stayPersistence.findById(stay.getId())).thenReturn(Optional.of(stay));

        StayDTO dto = useCase.execute(stay.getId());

        assertEquals(stay.getId(), dto.getStayId());
        assertEquals(stay.getSpotId(), dto.getSpotId());
    }

    @Test
    @DisplayName("estancia inexistente -> StayNotFoundException (404)")
    void execute_notFound_throws() {
        GetStayUseCase useCase = new GetStayUseCase(stayPersistence, stayDTOFactory);
        UUID stayId = UUID.randomUUID();
        when(stayPersistence.findById(stayId)).thenReturn(Optional.empty());

        assertThrows(StayNotFoundException.class, () -> useCase.execute(stayId));
    }
}
