package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.EntryTicketDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
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
 */
public class CheckInUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckInUseCase.class);

    private final StayPersistence stayPersistence;
    private final StayVehiclePort vehiclePort;
    private final StaySpotPort spotPort;
    private final StayTariffPort tariffPort;
    private final StayTicketPort ticketPort;
    private final StayDTOFactory stayDTOFactory;
    private final Clock clock;

    public CheckInUseCase(StayPersistence stayPersistence,
                          StayVehiclePort vehiclePort,
                          StaySpotPort spotPort,
                          StayTariffPort tariffPort,
                          StayTicketPort ticketPort,
                          StayDTOFactory stayDTOFactory,
                          Clock clock) {
        this.stayPersistence = stayPersistence;
        this.vehiclePort = vehiclePort;
        this.spotPort = spotPort;
        this.tariffPort = tariffPort;
        this.ticketPort = ticketPort;
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
        VehicleInfo vehicle = vehiclePort.getOrCreateVehicle(plate, command.getVehicleType());

        // 2. RN-11: un vehiculo dado de baja no puede entrar. Antes de tocar plaza.
        if (!vehicle.active()) {
            throw new VehicleDeactivatedException(
                    "El vehiculo con matricula " + plate + " esta dado de baja: acceso denegado (RN-11)");
        }

        // 3. IN-02 / IN-03 / CB-05: un vehiculo no puede entrar dos veces.
        if (stayPersistence.existsByVehicleIdAndStatus(vehicle.vehicleId(), StayStatus.IN_PROGRESS)) {
            throw new DuplicateActiveStayException(
                    "El vehiculo con matricula " + plate
                            + " ya tiene una estancia en curso: acceso denegado (IN-02, CB-05)");
        }

        // 4. Ocupar plaza: comprobacion + reserva atomica (RN-05, CA3). Lanza
        //    NoAvailableSpotException si el parking esta completo (RN-01, CA-01).
        UUID spotId = spotPort.occupySpot(vehicle.vehicleType());

        // 5. A partir de aqui la plaza esta OCCUPIED: cualquier fallo debe liberarla.
        try {
            UUID tariffId = tariffPort.getActiveTariffId(vehicle.vehicleType());

            Stay stay = Stay.checkIn(
                    UUID.randomUUID(),
                    vehicle.vehicleId(),
                    vehicle.vehicleType(),
                    spotId,
                    tariffId,
                    clock.instant());

            Stay saved = stayPersistence.save(stay);

            // 6. Ticket de entrada (HU-01 CA3): sin el, el conductor no puede
            //    justificar la hora de entrada al salir. Un fallo aqui tambien
            //    compensa liberando la plaza, igual que un fallo al persistir.
            StayTicketPort.EntryTicketInfo ticket =
                    ticketPort.issueEntryTicket(saved.getId(), plate, saved.getCheckIn());

            EntryTicketDTO entryTicket = new EntryTicketDTO(
                    ticket.ticketId(), ticket.barCode(), ticket.issuedAt());

            return new CheckInResultDTO(stayDTOFactory.create(saved), entryTicket);

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
