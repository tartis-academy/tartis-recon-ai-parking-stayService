package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCheckOutDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventPublisher;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort.VehicleInfo;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckOutUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final String PLATE = "1234ABC";

    @Mock
    private StayPersistence stayPersistence;
    @Mock
    private StayVehiclePort vehiclePort;
    @Mock
    private StayTariffPort tariffPort;
    @Mock
    private StayEventPublisher eventPublisher;

    private CheckOutUseCase useCase;

    private UUID stayId;
    private UUID vehicleId;
    private UUID spotId;
    private UUID tariffId;
    private Instant checkIn;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        spotId = UUID.randomUUID();
        tariffId = UUID.randomUUID();
        checkIn = NOW.minus(90, ChronoUnit.MINUTES);
        useCase = new CheckOutUseCase(stayPersistence, vehiclePort, tariffPort, eventPublisher,
                new StayDTOFactory(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Stay inProgressStay() {
        return Stay.restore(stayId, vehicleId, VehicleType.CAR, spotId, tariffId,
                checkIn, null, null, StayStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Debe cerrar la estancia: calcula importe, finaliza y publica StayClosedEvent (ASY-03/ASY-04)")
    void shouldCheckOutActiveStay() {
        when(vehiclePort.findByPlate(PLATE))
                .thenReturn(Optional.of(new VehicleInfo(vehicleId, PLATE, VehicleType.CAR, true)));
        when(stayPersistence.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS))
                .thenReturn(Optional.of(inProgressStay()));
        when(tariffPort.calculateAmount(VehicleType.CAR, 90L)).thenReturn(new BigDecimal("3.00"));
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckOutResultDTO result = useCase.execute(new StayCheckOutDTO(PLATE, null));

        assertEquals(StayStatus.FINISHED, result.getStay().getStatus());
        assertEquals(0, result.getStay().getTotalAmount().compareTo(new BigDecimal("3.00")));
        assertEquals(NOW, result.getStay().getCheckOut());
        // El ticket ya no se conoce al responder: se genera de forma
        // asincrona al consumir el evento (ASY-04).
        assertNull(result.getExitTicketId());
        assertEquals(90L, result.getTotalMinutes());

        ArgumentCaptor<StayClosedEvent> eventCaptor = ArgumentCaptor.forClass(StayClosedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        StayClosedEvent published = eventCaptor.getValue();

        assertEquals("StayClosedEvent", published.type());
        assertEquals("v1", published.version());
        assertEquals(stayId, published.data().stayId());
        assertEquals(spotId, published.data().spotId());
        assertEquals(PLATE, published.data().plate());
        assertEquals(checkIn, published.data().entryDate());
        assertEquals(NOW, published.data().exitDate());
        assertEquals(0, published.data().totalAmount().compareTo(new BigDecimal("3.00")));
    }

    @Test
    @DisplayName("Debe lanzar StayNotFoundException si no hay estancia en curso para la matricula")
    void shouldFailWhenNoActiveStay() {
        when(vehiclePort.findByPlate(PLATE))
                .thenReturn(Optional.of(new VehicleInfo(vehicleId, PLATE, VehicleType.CAR, true)));
        when(stayPersistence.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        assertThrows(StayNotFoundException.class,
                () -> useCase.execute(new StayCheckOutDTO(PLATE, null)));

        verify(stayPersistence, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Debe lanzar StayNotFoundException si la matricula no existe en vehicle-service, sin tocar persistence")
    void shouldFailWhenVehicleUnknown() {
        when(vehiclePort.findByPlate(PLATE)).thenReturn(Optional.empty());

        assertThrows(StayNotFoundException.class,
                () -> useCase.execute(new StayCheckOutDTO(PLATE, null)));

        verifyNoInteractions(stayPersistence, tariffPort, eventPublisher);
    }

    @Test
    @DisplayName("Debe lanzar InvalidStayException si la matricula viene vacia, sin tocar los puertos")
    void shouldFailWhenPlateBlank() {
        assertThrows(InvalidStayException.class,
                () -> useCase.execute(new StayCheckOutDTO("   ", null)));

        verifyNoInteractions(vehiclePort, stayPersistence, tariffPort, eventPublisher);
    }

    @Test
    @DisplayName("Debe lanzar InvalidStayException si la matricula viene a null, sin tocar los puertos")
    void shouldFailWhenPlateNull() {
        assertThrows(InvalidStayException.class,
                () -> useCase.execute(new StayCheckOutDTO(null, null)));

        verifyNoInteractions(vehiclePort, stayPersistence, tariffPort, eventPublisher);
    }

    @Test
    @DisplayName("Si publicar StayClosedEvent falla, se registra pero no se propaga: el check-out ya es valido (requiere revision manual)")
    void shouldSwallowEventPublishFailure() {
        when(vehiclePort.findByPlate(PLATE))
                .thenReturn(Optional.of(new VehicleInfo(vehicleId, PLATE, VehicleType.CAR, true)));
        when(stayPersistence.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS))
                .thenReturn(Optional.of(inProgressStay()));
        when(tariffPort.calculateAmount(VehicleType.CAR, 90L)).thenReturn(new BigDecimal("3.00"));
        when(stayPersistence.save(any(Stay.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("rabbitmq no disponible"))
                .when(eventPublisher).publish(any(StayClosedEvent.class));

        CheckOutResultDTO result = useCase.execute(new StayCheckOutDTO(PLATE, null));

        assertEquals(StayStatus.FINISHED, result.getStay().getStatus());
        verify(eventPublisher).publish(any(StayClosedEvent.class));
    }
}