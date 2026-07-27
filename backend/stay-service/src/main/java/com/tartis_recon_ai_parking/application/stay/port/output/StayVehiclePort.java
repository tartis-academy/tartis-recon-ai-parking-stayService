package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import java.util.Optional;
import java.util.UUID;

public interface StayVehiclePort {
    /**
     * Obtiene la información del vehículo o lo crea si no existe (auto-registration).
     */
    VehicleInfo getOrCreateVehicle(String plate, VehicleType vehicleType);

    /**
     * Busca el vehículo por matrícula sin crearlo. Vacío si no existe.
     */
    Optional<VehicleInfo> findByPlate(String plate);

    record VehicleInfo(UUID vehicleId, String plate, VehicleType vehicleType, boolean active) {}
}