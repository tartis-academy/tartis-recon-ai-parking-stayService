package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;

import java.time.Instant;
import java.util.UUID;

public class CheckInUseCase {

    private final StayPersistence stayPersistence;
    private final StaySpotPort staySpotPort;
    private final StayTariffPort stayTariffPort;

    public CheckInUseCase(StayPersistence stayPersistence,
                           StaySpotPort staySpotPort,
                           StayTariffPort stayTariffPort) {
        this.stayPersistence = stayPersistence;
        this.staySpotPort = staySpotPort;
        this.stayTariffPort = stayTariffPort;
    }

    public Stay execute(String plate, VehicleType vehicleType) {
        if (stayPersistence.existsByPlateAndStatus(plate, StayStatus.IN_PROGRESS)) {
            throw new DuplicateActiveStayException(
                    "El vehiculo con matricula " + plate + " ya tiene una estancia en curso");
        }

        UUID spotId = staySpotPort.occupySpot(vehicleType);
        UUID tariffId = resolveTariffOrReleaseSpot(vehicleType, spotId);

        Stay stay = Stay.checkIn(UUID.randomUUID(), plate, vehicleType, spotId, tariffId, Instant.now());
        return stayPersistence.save(stay);
    }

    /**
     * IN-05: una plaza OCCUPIED debe tener siempre exactamente una Stay
     * asociada. Como occupySpot() ya se ejecuto con exito antes de este punto,
     * si falla la resolucion de tarifa hay que liberar esa plaza para no
     * dejarla huerfana (ocupada sin ninguna Stay real detras).
     */
    private UUID resolveTariffOrReleaseSpot(VehicleType vehicleType, UUID spotId) {
        try {
            return stayTariffPort.getActiveTariffId(vehicleType);
        } catch (RuntimeException tariffException) {
            try {
                staySpotPort.releaseSpot(spotId);
            } catch (RuntimeException releaseException) {
                // No ocultamos el fallo original (el de tarifa, que es la causa
                // real): anadimos el fallo de liberacion como "suppressed" para
                // no perder informacion de depuracion si tambien falla esto.
                tariffException.addSuppressed(releaseException);
            }
            throw tariffException;
        }
    }
}
