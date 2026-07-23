package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort.VehicleInfo;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleDeactivatedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Entrada de vehiculo (HU-01) con todos los puertos simulados.
 *
 * <p>Cubre el camino feliz (CA3), las tres denegaciones (RN-11, RN-01, CB-05) y la
 * compensacion que evita plazas ocupadas sin estancia (IN-25).
 */
@ExtendWith(MockitoExtension.class)
class CheckInUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-23T08:30:00Z");
    private static final String PLATE = "1234ABC";

    @Mock
    private StayPersistence stayPersistence;

    @Mock
    private StayVehiclePort vehiclePort;

    @Mock
    private StaySpotPort spotPort;

    @Mock
    private StayTariffPort tariffPort;

    private CheckInUseCase useCase;

    private UUID vehicleId;
    private UUID spotId;
    private UUID tariffId;

    @BeforeEach
    void setUp() {
        vehicleId = UUID.randomUUID();
        spotId = UUID.randomUUID();
        tariffId = UUID.randomUUID();
        useCase = new CheckInUseCase(stayPersistence, vehiclePort, spotPort, tariffPort,
                new StayDTOFactory(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------
    // Camino feliz (CA3 de HU-01)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("vehiculo activo y con plaza libre: abre estancia IN_PROGRESS")
    void shouldOpenStay_whenVehicleActiveAndSpotAvailable() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));

        StayDTO result = useCase.execute(new StayCreateDTO(PLATE, null));

        assertNotNull(result.getStayId());
        assertEquals(vehicleId, result.getVehicleId());
        assertEquals(spotId, result.getSpotId());
        assertEquals(tariffId, result.getTariffId());
        assertEquals(StayStatus.IN_PROGRESS, result.getStatus());
        assertEquals(NOW, result.getCheckIn());
        // IN-14 / IN-16: una estancia en curso no tiene ni salida ni importe.
        assertNull(result.getCheckOut());
        assertNull(result.getTotalAmount());
    }

    @Test
    @DisplayName("ocupa plaza del tipo que resuelve vehicle-service, no el detectado (CA2)")
    void shouldOccupySpotOfResolvedVehicleType() {
        // El totem detecta CAR, pero el vehiculo esta registrado como MOTORBIKE.
        when(vehiclePort.getOrCreateVehicle(PLATE, VehicleType.CAR))
                .thenReturn(new VehicleInfo(vehicleId, PLATE, VehicleType.MOTORBIKE, true));
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.MOTORBIKE)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.MOTORBIKE)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));

        StayDTO result = useCase.execute(new StayCreateDTO(PLATE, VehicleType.CAR));

        assertEquals(VehicleType.MOTORBIKE, result.getVehicleType());
        verify(spotPort).occupySpot(VehicleType.MOTORBIKE);
    }

    @Test
    @DisplayName("normaliza la matricula antes de resolver el vehiculo (IN-01, CB-01)")
    void shouldNormalizePlateBeforeResolving() {
        when(vehiclePort.getOrCreateVehicle(PLATE, null))
                .thenReturn(new VehicleInfo(vehicleId, PLATE, VehicleType.CAR, true));
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new StayCreateDTO(" 1234 abc ", null));

        verify(vehiclePort).getOrCreateVehicle(PLATE, null);
    }

    // ------------------------------------------------------------------
    // Denegaciones: la barrera permanece cerrada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RN-11: vehiculo dado de baja, sin llegar a tocar spot-service")
    void shouldDenyAccess_whenVehicleDeactivated() {
        givenVehicle(VehicleType.CAR, false);

        assertThrows(VehicleDeactivatedException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        // No hay plaza que compensar: nunca se ocupo ninguna.
        verifyNoInteractions(spotPort);
        verify(stayPersistence, never()).save(any());
    }

    @Test
    @DisplayName("IN-02 / CB-05: el vehiculo ya tiene una estancia en curso")
    void shouldDenyAccess_whenVehicleAlreadyInside() {
        givenVehicle(VehicleType.CAR, true);
        when(stayPersistence.existsByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS))
                .thenReturn(true);

        assertThrows(DuplicateActiveStayException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        verifyNoInteractions(spotPort);
        verify(stayPersistence, never()).save(any());
    }

    @Test
    @DisplayName("RN-01 / CA-01: parking completo, no se crea estancia ni se libera nada")
    void shouldDenyAccess_whenNoSpotAvailable() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR))
                .thenThrow(new NoAvailableSpotException("parking completo"));

        assertThrows(NoAvailableSpotException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        verify(stayPersistence, never()).save(any());
        verify(spotPort, never()).releaseSpot(any());
    }

    @Test
    @DisplayName("matricula vacia: peticion invalida, sin tocar puertos")
    void shouldFail_whenPlateIsBlank() {
        assertThrows(InvalidStayException.class,
                () -> useCase.execute(new StayCreateDTO("  ", VehicleType.CAR)));

        verifyNoInteractions(vehiclePort, spotPort, stayPersistence, tariffPort);
    }

    // ------------------------------------------------------------------
    // Compensacion (IN-25)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("si la estancia no se persiste, libera la plaza ya ocupada")
    void shouldReleaseSpot_whenStayCannotBePersisted() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class)))
                .thenThrow(new IllegalStateException("base de datos caida"));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        // Sin esto la plaza quedaria OCCUPIED sin estancia asociada (IN-25).
        verify(spotPort).releaseSpot(spotId);
    }

    @Test
    @DisplayName("si tampoco se puede liberar, gana el error original")
    void shouldPropagateOriginalError_whenCompensationAlsoFails() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        IllegalStateException original = new IllegalStateException("base de datos caida");
        when(stayPersistence.save(any(Stay.class))).thenThrow(original);
        RuntimeException releaseFailure = new IllegalStateException("spot-service caido");
        doThrow(releaseFailure).when(spotPort).releaseSpot(spotId);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        assertSame(original, thrown);
        assertSame(releaseFailure, thrown.getSuppressed()[0]);
    }

    @Test
    @DisplayName("persiste la estancia con las referencias resueltas por los puertos")
    void shouldPersistStayWithResolvedReferences() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new StayCreateDTO(PLATE, null));

        ArgumentCaptor<Stay> captor = ArgumentCaptor.forClass(Stay.class);
        verify(stayPersistence).save(captor.capture());
        Stay persisted = captor.getValue();

        assertEquals(vehicleId, persisted.getVehicleId());
        assertEquals(spotId, persisted.getSpotId());
        assertEquals(tariffId, persisted.getTariffId());
        assertEquals(NOW, persisted.getCheckIn());
        assertEquals(StayStatus.IN_PROGRESS, persisted.getStatus());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void givenVehicle(VehicleType type, boolean active) {
        when(vehiclePort.getOrCreateVehicle(eq(PLATE), any()))
                .thenReturn(new VehicleInfo(vehicleId, PLATE, type, active));
    }

    private void givenNoActiveStay() {
        when(stayPersistence.existsByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS))
                .thenReturn(false);
    }
}
