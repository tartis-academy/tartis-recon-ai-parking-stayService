package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCheckOutDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventPublisher;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort.VehicleInfo;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

public class CheckOutUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckOutUseCase.class);

    private final StayPersistence stayPersistence;
    private final StayVehiclePort vehiclePort;
    private final StayTariffPort tariffPort;
    private final StayEventPublisher eventPublisher;
    private final StayEventStreamPublisher eventStreamPublisher;
    private final StayDTOFactory stayDTOFactory;
    private final Clock clock;

    public CheckOutUseCase(StayPersistence stayPersistence,
                           StayVehiclePort vehiclePort,
                           StayTariffPort tariffPort,
                           StayEventPublisher eventPublisher,
                           StayEventStreamPublisher eventStreamPublisher,
                           StayDTOFactory stayDTOFactory,
                           Clock clock) {
        this.stayPersistence = stayPersistence;
        this.vehiclePort = vehiclePort;
        this.tariffPort = tariffPort;
        this.eventPublisher = eventPublisher;
        this.eventStreamPublisher = eventStreamPublisher;
        this.stayDTOFactory = stayDTOFactory;
        this.clock = clock;
    }

    public CheckOutResultDTO execute(StayCheckOutDTO command) {
        String plate = normalizePlate(command.getPlate());

        VehicleInfo vehicle = vehiclePort.findByPlate(plate)
                .orElseThrow(() -> new StayNotFoundException(
                        "No existe ninguna estancia en curso para la matricula " + plate + " (HU-02 CA-02)"));

        Stay stay = stayPersistence.findByVehicleIdAndStatus(vehicle.vehicleId(), StayStatus.IN_PROGRESS)
                .orElseThrow(() -> new StayNotFoundException(
                        "No existe ninguna estancia en curso para la matricula " + plate + " (HU-02 CA-02)"));

        Instant checkOut = clock.instant();
        long totalMinutes = stay.parkedMinutesUntil(checkOut);

        // Unica llamada sincrona que queda: el importe tiene que ir en esta
        // misma respuesta.
        BigDecimal amount = tariffPort.calculateAmount(stay.getVehicleType(), totalMinutes);

        Stay finished = stay.finish(checkOut, amount);
        Stay saved = stayPersistence.save(finished);

        // Publicamos DESPUES de guardar: el check-out ya es valido en BD, el
        // evento es un efecto secundario. Si falla la publicacion NO
        // deshacemos el check-out (ver publishStayClosedEventQuietly).
        publishStayClosedEventQuietly(saved, plate);

        // exitTicketId ya no se conoce al responder: se genera al consumir
        // el evento, de forma asincrona. Es opcional en el contrato REST.
        return new CheckOutResultDTO(stayDTOFactory.create(saved), null, totalMinutes);
    }

    private void publishStayClosedEventQuietly(Stay stay, String plate) {
        StayClosedEvent event = StayClosedEvent.of(
                stay.getId(),
                stay.getSpotId(),
                plate,
                stay.getCheckIn(),
                stay.getCheckOut(),
                stay.getTotalAmount(),
                clock.instant());

        try {
            eventPublisher.publish(event);
            log.info("StayClosedEvent publicado para la estancia {}", stay.getId());
        } catch (RuntimeException e) {
            // La estancia ya quedo FINISHED y persistida: es la fuente de
            // verdad. Si el broker falla, no bloqueamos al cliente, pero
            // ticket-service y spot-service no se enteraran de este cierre
            // (requiere revision manual).
            log.error("Check-out realizado pero no se pudo publicar StayClosedEvent para la estancia {}:"
                    + " ticket-service y spot-service no se enteraran de este cierre (requiere revision manual)",
                    stay.getId(), e);
        }

        try {
            eventStreamPublisher.publish(event);
        } catch (RuntimeException e) {
            // Canal best-effort: no afecta al check-out ni a RabbitMQ.
            log.warn("No se pudo reenviar StayClosedEvent por SSE para la estancia {}", stay.getId(), e);
        }
    }

    private static String normalizePlate(String plate) {
        if (plate == null || plate.isBlank()) {
            throw new InvalidStayException("La matricula es obligatoria para el check-out");
        }
        return plate.replace(" ", "").trim().toUpperCase();
    }
}