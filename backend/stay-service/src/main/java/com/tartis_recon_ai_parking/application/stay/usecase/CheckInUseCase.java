package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.EntryTicketDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreatedEvent;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;

import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort.VehicleInfo;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleDeactivatedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.UUID;

/**
 * Entrada de vehiculo (HU-01): resuelve el vehiculo, ocupa una plaza y abre la
 * estancia.
 *
 * <p>POJO sin dependencias de Spring/JPA/HTTP (IN-35): se comunica con el exterior
 * solo a traves de puertos. Lo instancia una clase {@code @Configuration} de
 * infrastructure.
 *
 * <p><b>Orden de los pasos.</b> Las dos comprobaciones que deniegan sin efectos
 * secundarios —vehiculo dado de baja (RN-11) y vehiculo ya dentro (IN-02, CB-05)—
 * van <b>antes</b> de ocupar la plaza, para no tener que compensar una ocupacion
 * que no debio producirse. La ocupacion de plaza es la primera operacion con efecto
 * externo real.
 *
 * <p><b>Ocupar plaza es la autorizacion de acceso.</b> No se consulta la
 * disponibilidad por separado: entre consultar y ocupar cabe otro check-in y dos
 * vehiculos recibirian la misma plaza (rompe RN-05). {@link StaySpotPort#occupySpot}
 * comprueba y reserva de forma atomica (CA1, CA3, CA5 de HU-01). Si no hay plaza,
 * lanza {@link com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException}
 * (RN-01): se deniega el acceso, la barrera sigue cerrada y se informa (CA-01).
 *
 * <h2>Condiciones de carrera</h2>
 *
 * <p>Entre el paso 3 (comprobar si el vehiculo ya esta dentro) y el paso 7
 * (guardar la estancia) hay una ventana de milisegundos ocupada por tres
 * llamadas HTTP. Si en esa ventana entra un segundo check-in de la misma
 * matricula —dos operarios en dos totems, o el mismo totem reintentando tras un
 * timeout— los dos pasan la comprobacion del paso 3, porque ninguno ha guardado
 * todavia. Resultado sin proteccion: el mismo coche dentro dos veces, ocupando
 * dos plazas fisicas y con dos tickets de entrada validos.
 *
 * <p>Ninguna comprobacion en Java puede cerrar esa ventana, porque stay-service
 * corre con varias replicas y cada una tiene su propia JVM. La cierra el indice
 * unico parcial {@code ux_stays_one_active_per_vehicle} de la base de datos
 * (ver {@code V2__race_conditions.sql}), que es el unico punto que ven todas las
 * replicas a la vez. {@code StayPersistenceAdapter} traduce esa violacion a
 * {@link DuplicateActiveStayException}, la misma excepcion que lanza la
 * comprobacion del paso 3, asi que el cliente recibe el mismo 409 tanto si el
 * duplicado se detecta pronto como si se detecta en el ultimo momento.
 *
 * <p><b>La comprobacion del paso 3 no sobra</b> por tener el indice detras:
 * resuelve el caso normal (el coche lleva dentro un rato) sin ocupar una plaza
 * ni emitir un ticket para luego tener que deshacerlo. El indice cubre solo el
 * caso raro de las dos peticiones simultaneas.
 */
public class CheckInUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckInUseCase.class);

    private final StayPersistence stayPersistence;
    private final StayVehiclePort vehiclePort;
    private final StaySpotPort spotPort;
    private final StayTariffPort tariffPort;
    private final StayTicketPort ticketPort;
    private final StayEventStreamPublisher eventStreamPublisher;
    private final StayDTOFactory stayDTOFactory;
    private final Clock clock;

    /**
     * Constructor sobrecargado para mantener compatibilidad hacia atras con tests y llamantes
     * que no requieren emision de eventos SSE (asigna null a eventStreamPublisher).
     */
    public CheckInUseCase(StayPersistence stayPersistence,
                          StayVehiclePort vehiclePort,
                          StaySpotPort spotPort,
                          StayTariffPort tariffPort,
                          StayTicketPort ticketPort,
                          StayDTOFactory stayDTOFactory,
                          Clock clock) {
        this(stayPersistence, vehiclePort, spotPort, tariffPort, ticketPort, null, stayDTOFactory, clock);
    }

    /**
     * Constructor principal que incluye el emisor SSE (StayEventStreamPublisher) para
     * notificar la entrada de vehiculo en tiempo real (SSE-04).
     */
    public CheckInUseCase(StayPersistence stayPersistence,
                          StayVehiclePort vehiclePort,
                          StaySpotPort spotPort,
                          StayTariffPort tariffPort,
                          StayTicketPort ticketPort,
                          StayEventStreamPublisher eventStreamPublisher,
                          StayDTOFactory stayDTOFactory,
                          Clock clock) {
        this.stayPersistence = stayPersistence;
        this.vehiclePort = vehiclePort;
        this.spotPort = spotPort;
        this.tariffPort = tariffPort;
        this.ticketPort = ticketPort;
        this.eventStreamPublisher = eventStreamPublisher;
        this.stayDTOFactory = stayDTOFactory;
        this.clock = clock;
    }


    /**
     * @throws VehicleDeactivatedException  el vehiculo esta dado de baja (RN-11)
     * @throws DuplicateActiveStayException el vehiculo ya tiene estancia en curso (IN-02, CB-05)
     * @throws com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException
     *         no hay plazas para su tipo (RN-01, CA-01 de HU-01)
     * @throws InvalidStayException         la matricula viene vacia
     */
    public CheckInResultDTO execute(StayCreateDTO command) {
        String plate = normalizePlate(command.getPlate());

        // 1. Resolver el vehiculo. El puerto hace el GET /v1/vehicles/plate/{plate}
        //    y, si no existe (404), lo da de alta con POST /v1/vehicles.
        VehicleInfo vehicle = vehiclePort.getOrCreateVehicle(
                plate, command.getVehicleType(), command.getVehicleAttributes());

        // 2. RN-11: un vehiculo dado de baja no puede entrar. Antes de tocar plaza.
        if (!vehicle.active()) {
            throw new VehicleDeactivatedException(
                    "El vehiculo con matricula " + plate + " esta dado de baja: acceso denegado (RN-11)");
        }

        // 3. IN-02 / IN-03 / CB-05: un vehiculo no puede entrar dos veces.
        //    Esta comprobacion resuelve el caso normal y evita ocupar plaza y
        //    emitir ticket para nada. El caso de dos check-in simultaneos NO lo
        //    cubre (ver "Condiciones de carrera" en el javadoc de la clase): de
        //    ese se encarga el indice unico de la base de datos en el paso 7.
        if (stayPersistence.existsByVehicleIdAndStatus(vehicle.vehicleId(), StayStatus.IN_PROGRESS)) {
            throw new DuplicateActiveStayException(
                    "El vehiculo con matricula " + plate
                            + " ya tiene una estancia en curso: acceso denegado (IN-02, CB-05)");
        }

        // 4. Ocupar plaza: comprobacion + reserva atomica (RN-05, CA3). Lanza
        //    NoAvailableSpotException si el parking esta completo (RN-01, CA-01).
        UUID spotId = spotPort.occupySpot(vehicle.vehicleType());

        // 5. A partir de aqui la plaza esta OCCUPIED: cualquier fallo debe liberarla.
        //    El ticket se declara fuera del try porque el manejo del doble
        //    check-in necesita saber, desde el catch, si llego a emitirse.
        StayTicketPort.EntryTicketInfo ticket = null;
        try {
            UUID tariffId = tariffPort.getActiveTariffId(vehicle.vehicleType());

            Stay stay = Stay.checkIn(
                    UUID.randomUUID(),
                    vehicle.vehicleId(),
                    vehicle.vehicleType(),
                    spotId,
                    tariffId,
                    clock.instant());

            // 6. Ticket de entrada (HU-01 CA3) ANTES de persistir la estancia.
            //    El id de la estancia ya existe en memoria (lo genera este caso de
            //    uso, no la BD), asi que no hace falta esperar al save() para
            //    emitir el ticket. De este modo, si ticket-service falla o esta
            //    caido, la estancia nunca llega a guardarse: el catch solo tiene
            //    que liberar la plaza y no queda una estancia huerfana en BD que
            //    bloquee reintentos futuros del mismo vehiculo (IN-02, CB-05).
            ticket = ticketPort.issueEntryTicket(stay.getId(), plate, stay.getCheckIn());

            // 7. Ultima linea de defensa contra el doble check-in. Si otra
            //    peticion de la misma matricula gano la carrera mientras
            //    haciamos las llamadas de arriba, el indice unico parcial de la
            //    BD hace saltar aqui una DuplicateActiveStayException. El catch
            //    de abajo libera la plaza que acabamos de ocupar.
            Stay saved = stayPersistence.save(stay);

            publishStayCreatedEventQuietly(saved, plate);

            EntryTicketDTO entryTicket = new EntryTicketDTO(
                    ticket.ticketId(), ticket.barCode(), ticket.issuedAt());

            return new CheckInResultDTO(stayDTOFactory.create(saved), entryTicket);

        } catch (DuplicateActiveStayException e) {
            // Caso raro y con una consecuencia que conviene dejar por escrito:
            // el ticket de entrada del paso 6 YA se ha emitido y aqui no se
            // puede anular (ticket-service no expone esa operacion). Queda un
            // ticket huerfano, asociado a una estancia que no existe.
            //
            // Se asume a proposito. La alternativa —guardar antes de emitir el
            // ticket— cambiaria el fallo raro por uno peor y mas frecuente: si
            // ticket-service esta caido quedaria una estancia en BD sin ticket,
            // bloqueando todos los reintentos de ese vehiculo por IN-02. Un
            // ticket suelto no bloquea nada; solo ensucia el listado.
            log.warn("Doble check-in simultaneo de la matricula {}: gana la primera peticion."
                    + " El ticket de entrada {} queda huerfano (sin estancia asociada) y requiere"
                    + " limpieza manual", plate, ticket != null ? ticket.ticketId() : "(no emitido)", e);
            releaseQuietly(spotId, e);
            throw e;

        } catch (RuntimeException e) {
            releaseQuietly(spotId, e);
            throw e;
        }
    }

    /**
     * Compensacion: si la estancia no llega a persistirse, la plaza no puede quedar
     * ocupada sin estancia asociada (IN-25/IN-05).
     *
     * <p>Un fallo al liberar no debe tapar el error original (el que explica al
     * conductor por que no ha entrado); se registra y se suprime, dejando la plaza
     * para el saneamiento manual del administrador.
     */
    private void releaseQuietly(UUID spotId, RuntimeException original) {
        try {
            spotPort.releaseSpot(spotId);
            log.warn("Check-in fallido tras ocupar la plaza {}: liberada por compensacion", spotId, original);
        } catch (RuntimeException releaseFailure) {
            original.addSuppressed(releaseFailure);
            log.error("La plaza {} ha quedado OCCUPIED sin estancia asociada: requiere"
                    + " liberacion manual del administrador (IN-25)", spotId, releaseFailure);
        }
    }

    private void publishStayCreatedEventQuietly(Stay stay, String plate) {
        if (eventStreamPublisher == null) {
            return;
        }
        StayCreatedEvent event = StayCreatedEvent.of(
                stay.getId(),
                stay.getVehicleId(),
                stay.getVehicleType(),
                stay.getSpotId(),
                stay.getTariffId(),
                plate,
                stay.getCheckIn(),
                clock.instant());

        try {
            eventStreamPublisher.publish(event);
            log.info("StayCreatedEvent publicado por SSE para la estancia {}", stay.getId());
        } catch (RuntimeException e) {
            log.warn("No se pudo reenviar StayCreatedEvent por SSE para la estancia {}", stay.getId(), e);
        }
    }

    /**
     * Normaliza la matricula a mayusculas y sin espacios. Las lecturas de camara y
     * el tecleo manual del totem (CB-01) llegan con formatos distintos, e IN-01 exige
     * que la matricula identifique al vehiculo de forma univoca.
     */

    private static String normalizePlate(String plate) {
        if (plate == null || plate.isBlank()) {
            throw new InvalidStayException("La matricula es obligatoria para el check-in");
        }
        return plate.replace(" ", "").trim().toUpperCase();
    }
}
