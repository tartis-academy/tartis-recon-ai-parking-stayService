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

/**
 * Salida de vehiculo (HU-02): cierra la estancia en curso, calcula el importe y
 * publica el cierre para que ticket-service y spot-service reaccionen.
 *
 * <h2>Condiciones de carrera</h2>
 *
 * <p>Entre leer la estancia y guardarla cerrada hay una llamada HTTP a
 * tariff-service. En esa ventana cabe un segundo check-out del mismo vehiculo
 * (doble pulsacion en el totem, reintento tras timeout, dos operarios). Sin
 * proteccion, los dos leen la estancia en IN_PROGRESS, los dos la dan por buena
 * y pasan tres cosas:
 *
 * <ol>
 *   <li>la segunda escritura pisa a la primera (<em>lost update</em>): si el
 *       reloj avanzo entre ambas, el importe cobrado no es el que se calculo;</li>
 *   <li>se publican <b>dos</b> {@code StayClosedEvent}, asi que ticket-service
 *       emite dos tickets de salida para la misma estancia;</li>
 *   <li>spot-service libera la plaza dos veces. La segunda liberacion es la
 *       peligrosa: si entre medias entro otro coche a esa misma plaza, queda
 *       marcada como libre con un vehiculo dentro y se le asignara a un
 *       tercero.</li>
 * </ol>
 *
 * <p>Lo cierra el bloqueo optimista de {@code StayEntity}: la version leida
 * viaja dentro del propio {@link Stay} hasta el {@code save}, y el UPDATE lleva
 * un {@code WHERE version = ?} que la segunda peticion ya no cumple. El
 * adaptador de persistencia lo traduce a
 * {@link com.tartis_recon_ai_parking.domain.stay.exception.ConcurrentStayModificationException}
 * (409).
 *
 * <p><b>El orden de los dos ultimos pasos es lo que hace que esto funcione.</b>
 * Guardar va antes que publicar, asi que la peticion que pierde la carrera
 * revienta en el {@code save} y no llega nunca al {@code publish}. Si el evento
 * se publicara primero, el bloqueo optimista no serviria de nada: el segundo
 * ticket de salida y la segunda liberacion de plaza ya se habrian ido por el
 * broker antes de que la base de datos tuviera ocasion de decir que no.
 */
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

        // finish() arrastra la version que traia la estancia leida: es lo que
        // permite que el UPDATE detecte si otro check-out se ha adelantado.
        Stay finished = stay.finish(checkOut, amount);

        // Si perdemos la carrera, aqui salta ConcurrentStayModificationException
        // y la ejecucion termina: no se publica nada. Ver el javadoc de la clase.
        Stay saved = stayPersistence.save(finished);

        // Publicamos DESPUES de guardar, y este orden es deliberado por dos
        // motivos distintos:
        //   - el check-out ya es valido en BD, el evento es un efecto
        //     secundario: si falla la publicacion NO deshacemos el check-out
        //     (ver publishStayClosedEventQuietly);
        //   - y es lo que impide que un doble check-out publique dos eventos,
        //     porque el segundo ni siquiera llega hasta aqui.
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