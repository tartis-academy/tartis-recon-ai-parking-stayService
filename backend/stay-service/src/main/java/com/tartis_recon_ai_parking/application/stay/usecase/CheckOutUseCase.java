package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCheckOutDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
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
import java.util.UUID;

public class CheckOutUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckOutUseCase.class);

    private final StayPersistence stayPersistence;
    private final StayVehiclePort vehiclePort;
    private final StayTariffPort tariffPort;
    private final StaySpotPort spotPort;
    private final StayTicketPort ticketPort;
    private final StayDTOFactory stayDTOFactory;
    private final Clock clock;

    public CheckOutUseCase(StayPersistence stayPersistence,
                           StayVehiclePort vehiclePort,
                           StayTariffPort tariffPort,
                           StaySpotPort spotPort,
                           StayTicketPort ticketPort,
                           StayDTOFactory stayDTOFactory,
                           Clock clock) {
        this.stayPersistence = stayPersistence;
        this.vehiclePort = vehiclePort;
        this.tariffPort = tariffPort;
        this.spotPort = spotPort;
        this.ticketPort = ticketPort;
        this.stayDTOFactory = stayDTOFactory;
        this.clock = clock;
    }

    public CheckOutResultDTO execute(StayCheckOutDTO command) {
        String plate = normalizePlate(command.getPlate());

        VehicleInfo vehicle = vehiclePort.getOrCreateVehicle(plate, null);

        Stay stay = stayPersistence.findByVehicleIdAndStatus(vehicle.vehicleId(), StayStatus.IN_PROGRESS)
                .orElseThrow(() -> new StayNotFoundException(
                        "No existe ninguna estancia en curso para la matricula " + plate + " (HU-02 CA-02)"));

        Instant checkOut = clock.instant();

        BigDecimal amount = tariffPort.calculateAmount(stay.getTariffId(), stay.getCheckIn(), checkOut);

        Stay finished = stay.finish(checkOut, amount);
        Stay saved = stayPersistence.save(finished);

        UUID exitTicketId = ticketPort.issueExitTicket(saved.getId(), command.getEntryTicketId());

        releaseSpotQuietly(saved.getSpotId());

        long totalMinutes = saved.parkedMinutes();
        return new CheckOutResultDTO(stayDTOFactory.create(saved), exitTicketId, totalMinutes);
    }

    private void releaseSpotQuietly(UUID spotId) {
        try {
            spotPort.releaseSpot(spotId);
        } catch (RuntimeException e) {
            log.error("Check-out realizado pero la plaza {} no se pudo liberar:"
                    + " requiere liberacion manual del administrador (IN-25)", spotId, e);
        }
    }

    private static String normalizePlate(String plate) {
        if (plate == null || plate.isBlank()) {
            throw new InvalidStayException("La matricula es obligatoria para el check-out");
        }
        return plate.replace(" ", "").trim().toUpperCase();
    }
}
