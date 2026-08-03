package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreatedEvent;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort.EntryTicketInfo;
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

    @Mock
    private StayTicketPort ticketPort;

    @Mock
    private StayEventStreamPublisher eventStreamPublisher;

    private CheckInUseCase useCase;

    private UUID vehicleId;
    private UUID spotId;
    private UUID tariffId;

    @BeforeEach
    void setUp() {
        vehicleId = UUID.randomUUID();
        spotId = UUID.randomUUID();
        tariffId = UUID.randomUUID();
        useCase = new CheckInUseCase(stayPersistence, vehiclePort, spotPort, tariffPort, ticketPort,
                eventStreamPublisher, new StayDTOFactory(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Emisión de ticket por defecto para los caminos que llegan a persistir la estancia. */
    private void givenTicketIssued() {
        when(ticketPort.issueEntryTicket(any(), any(), any()))
                .thenReturn(new EntryTicketInfo(UUID.randomUUID(), "BC-0001", NOW));
    }

    // ------------------------------------------------------------------
    // Camino feliz (CA3 de HU-01)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("vehiculo activo y con plaza libre: abre estancia IN_PROGRESS y emite ticket de entrada")
    void shouldOpenStay_whenVehicleActiveAndSpotAvailable() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID ticketId = UUID.randomUUID();
        when(ticketPort.issueEntryTicket(any(), eq(PLATE), eq(NOW)))
                .thenReturn(new EntryTicketInfo(ticketId, "BC-0001", NOW));

        CheckInResultDTO result = useCase.execute(new StayCreateDTO(PLATE, null));

        assertNotNull(result.getStay().getStayId());
        assertEquals(vehicleId, result.getStay().getVehicleId());
        assertEquals(spotId, result.getStay().getSpotId());
        assertEquals(tariffId, result.getStay().getTariffId());
        assertEquals(StayStatus.IN_PROGRESS, result.getStay().getStatus());
        assertEquals(NOW, result.getStay().getCheckIn());
        // IN-14 / IN-16: una estancia en curso no tiene ni salida ni importe.
        assertNull(result.getStay().getCheckOut());
        assertNull(result.getStay().getTotalAmount());
        // El ticket de entrada emitido por ticket-service llega hasta el resultado.
        assertEquals(ticketId, result.getEntryTicket().getTicketId());
        assertEquals("BC-0001", result.getEntryTicket().getBarCode());
        assertEquals(NOW, result.getEntryTicket().getIssuedAt());

        verify(eventStreamPublisher).publish(any(StayCreatedEvent.class));
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
        givenTicketIssued();

        CheckInResultDTO result = useCase.execute(new StayCreateDTO(PLATE, VehicleType.CAR));

        assertEquals(VehicleType.MOTORBIKE, result.getStay().getVehicleType());
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
        givenTicketIssued();

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
        verifyNoInteractions(ticketPort);
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
        verifyNoInteractions(ticketPort);
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
        verifyNoInteractions(ticketPort);
    }

    @Test
    @DisplayName("matricula vacia: peticion invalida, sin tocar puertos")
    void shouldFail_whenPlateIsBlank() {
        assertThrows(InvalidStayException.class,
                () -> useCase.execute(new StayCreateDTO("  ", VehicleType.CAR)));

        verifyNoInteractions(vehiclePort, spotPort, stayPersistence, tariffPort, ticketPort);
    }

    // ------------------------------------------------------------------
    // Compensacion (IN-25)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("si la estancia no se persiste (con el ticket ya emitido), libera la plaza ya ocupada")
    void shouldReleaseSpot_whenStayCannotBePersisted() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        givenTicketIssued();
        when(stayPersistence.save(any(Stay.class)))
                .thenThrow(new IllegalStateException("base de datos caida"));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        // Sin esto la plaza quedaria OCCUPIED sin estancia asociada (IN-25).
        verify(spotPort).releaseSpot(spotId);
        // El ticket se emite antes de persistir, asi que en este escenario si se
        // llega a invocar (puede quedar un ticket huerfano en ticket-service, pero
        // eso no bloquea reintentos del vehiculo como si haria una estancia huerfana).
        verify(ticketPort).issueEntryTicket(any(), any(), any());
    }

    @Test
    @DisplayName("si falla la emision del ticket de entrada, libera la plaza y NO persiste la estancia (evita huerfanos, IN-25)")
    void shouldReleaseSpot_whenEntryTicketCannotBeIssued() {
        givenVehicle(VehicleType.CAR, true);
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        when(ticketPort.issueEntryTicket(any(), any(), any()))
                .thenThrow(new IllegalStateException("ticket-service caido"));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        verify(spotPort).releaseSpot(spotId);
        // Bloqueante corregido: si ticket-service falla, la estancia nunca debe
        // llegar a guardarse, o el vehiculo quedaria bloqueado por
        // DuplicateActiveStayException en el siguiente intento de check-in.
        verify(stayPersistence, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Condiciones de carrera
    // ------------------------------------------------------------------

    @Test
    @DisplayName("doble check-in simultaneo: si la BD rechaza el duplicado, libera la plaza y propaga el 409")
    void shouldReleaseSpot_whenDatabaseRejectsConcurrentDuplicate() {
        givenVehicle(VehicleType.CAR, true);
        // La comprobacion previa dice que no hay estancia activa: en el momento
        // de mirar, era verdad. La otra peticion guarda entre esa consulta y
        // nuestro save, que es exactamente la ventana que no se puede cerrar
        // desde Java. Quien lanza aqui es el indice unico de la base de datos,
        // traducido por StayPersistenceAdapter.
        givenNoActiveStay();
        when(spotPort.occupySpot(VehicleType.CAR)).thenReturn(spotId);
        when(tariffPort.getActiveTariffId(VehicleType.CAR)).thenReturn(tariffId);
        givenTicketIssued();
        when(stayPersistence.save(any(Stay.class))).thenThrow(new DuplicateActiveStayException(
                "El vehiculo " + vehicleId + " ya tiene una estancia en curso"));

        assertThrows(DuplicateActiveStayException.class,
                () -> useCase.execute(new StayCreateDTO(PLATE, null)));

        // Lo importante: la plaza que habiamos ocupado se devuelve. Si no, cada
        // carrera perdida dejaria una plaza inutilizada para siempre.
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
        givenTicketIssued();

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
