package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import java.util.UUID;

public interface StaySpotPort {

    /**
     * Busca una plaza disponible según el tipo de vehículo y la reserva/asigna.
     * Devuelve el ID de la plaza asignada.
     */
    UUID assignSpot(VehicleType vehicleType);

    /**
     * Libera la plaza al hacer check-out o al cancelar la estancia.
     */
    void releaseSpot(UUID spotId);
}