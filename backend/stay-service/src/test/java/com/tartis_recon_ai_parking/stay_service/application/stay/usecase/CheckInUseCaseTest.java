package com.tartis_recon_ai_parking.stay_service.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoActiveTariffException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de CheckInUseCase: comprobacion de duplicado (CB-05/IN-02), ocupacion
 * de plaza, resolucion de tarifa, y creacion/persistencia de la Stay.
 */
@ExtendWith(MockitoExtension.class)
class CheckInUseCaseTest {

    @Mock
    private StayPersistence stayPersistence;

    @Mock
    private StaySpotPort staySpotPort;

    @Mock
    private StayTariffPort stayTariffPort;

    private static final String PLATE = "1234ABC";
    private static final VehicleType VEHICLE_TYPE = VehicleType.CAR;

    @Test
    @DisplayName("Camino feliz: sin duplicado, ocupa plaza, resuelve tarifa, crea y guarda la Stay")
    void execute_happyPath_createsAndSavesStay() {
        UUID spotId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();

        when(stayPersistence.existsByPlateAndStatus(PLATE, StayStatus.IN_PROGRESS)).thenReturn(false);
        when(staySpotPort.occupySpot(VEHICLE_TYPE)).thenReturn(spotId);
        when(stayTariffPort.getActiveTariffId(VEHICLE_TYPE)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckInUseCase useCase = new CheckInUseCase(stayPersistence, staySpotPort, stayTariffPort);
        Stay result = useCase.execute(PLATE, VEHICLE_TYPE);

        assertEquals(PLATE, result.getPlate());
        assertEquals(VEHICLE_TYPE, result.getVehicleType());
        assertEquals(spotId, result.getSpotId());
        assertEquals(tariffId, result.getTariffId());
        assertEquals(StayStatus.IN_PROGRESS, result.getStatus());
        assertNull(result.getCheckOut());

        verify(stayPersistence).save(any(Stay.class));
    }

    @Test
    @DisplayName("CB-05/IN-02: matricula con estancia activa lanza DuplicateActiveStayException "
            + "y NO llega a ocupar ninguna plaza")
    void execute_duplicateActiveStay_throwsBeforeOccupyingSpot() {
        when(stayPersistence.existsByPlateAndStatus(PLATE, StayStatus.IN_PROGRESS)).thenReturn(true);

        CheckInUseCase useCase = new CheckInUseCase(stayPersistence, staySpotPort, stayTariffPort);

        assertThrows(DuplicateActiveStayException.class,
                () -> useCase.execute(PLATE, VEHICLE_TYPE));

        // Verificacion clave: no se debe gastar una plaza en una peticion que se va a denegar.
        verify(staySpotPort, never()).occupySpot(any());
        verify(stayTariffPort, never()).getActiveTariffId(any());
        verify(stayPersistence, never()).save(any());
    }

    @Test
    @DisplayName("RN-01: si no hay plaza disponible, la excepcion de StaySpotPort se propaga "
            + "y no se llega a resolver tarifa ni a guardar")
    void execute_noAvailableSpot_propagatesException() {
        when(stayPersistence.existsByPlateAndStatus(PLATE, StayStatus.IN_PROGRESS)).thenReturn(false);
        when(staySpotPort.occupySpot(VEHICLE_TYPE))
                .thenThrow(new NoAvailableSpotException("No hay plazas disponibles para el tipo CAR"));

        CheckInUseCase useCase = new CheckInUseCase(stayPersistence, staySpotPort, stayTariffPort);

        assertThrows(NoAvailableSpotException.class,
                () -> useCase.execute(PLATE, VEHICLE_TYPE));

        verify(stayTariffPort, never()).getActiveTariffId(any());
        verify(stayPersistence, never()).save(any());
    }

    @Test
    @DisplayName("Sin tarifa activa: la excepcion se propaga, no se guarda nada, "
            + "Y la plaza ya ocupada se libera (compensacion, IN-05)")
    void execute_noActiveTariff_releasesTheAlreadyOccupiedSpot() {
        UUID spotId = UUID.randomUUID();

        when(stayPersistence.existsByPlateAndStatus(PLATE, StayStatus.IN_PROGRESS)).thenReturn(false);
        when(staySpotPort.occupySpot(VEHICLE_TYPE)).thenReturn(spotId);
        when(stayTariffPort.getActiveTariffId(VEHICLE_TYPE))
                .thenThrow(new NoActiveTariffException("No hay tarifa activa para el tipo CAR"));

        CheckInUseCase useCase = new CheckInUseCase(stayPersistence, staySpotPort, stayTariffPort);

        assertThrows(NoActiveTariffException.class,
                () -> useCase.execute(PLATE, VEHICLE_TYPE));

        verify(stayPersistence, never()).save(any());
        // La compensacion: la plaza que se ocupo se libera para no dejarla huerfana.
        verify(staySpotPort).releaseSpot(spotId);
    }

    @Test
    @DisplayName("Si ademas falla la liberacion de la plaza, se sigue propagando "
            + "la excepcion ORIGINAL de tarifa (no la de liberacion), como suppressed")
    void execute_noActiveTariff_andReleaseAlsoFails_stillPropagatesOriginalException() {
        UUID spotId = UUID.randomUUID();
        NoActiveTariffException tariffException =
                new NoActiveTariffException("No hay tarifa activa para el tipo CAR");

        when(stayPersistence.existsByPlateAndStatus(PLATE, StayStatus.IN_PROGRESS)).thenReturn(false);
        when(staySpotPort.occupySpot(VEHICLE_TYPE)).thenReturn(spotId);
        when(stayTariffPort.getActiveTariffId(VEHICLE_TYPE)).thenThrow(tariffException);
        doThrow(new RuntimeException("spot-service caido"))
                .when(staySpotPort).releaseSpot(spotId);

        CheckInUseCase useCase = new CheckInUseCase(stayPersistence, staySpotPort, stayTariffPort);

        NoActiveTariffException thrown = assertThrows(NoActiveTariffException.class,
                () -> useCase.execute(PLATE, VEHICLE_TYPE));

        assertSame(tariffException, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        verify(stayPersistence, never()).save(any());
    }
}