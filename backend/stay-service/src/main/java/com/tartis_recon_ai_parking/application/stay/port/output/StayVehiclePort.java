package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.application.stay.dto.VehicleAttributes;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import java.util.Optional;
import java.util.UUID;

public interface StayVehiclePort {
    /**
     * Obtiene la información del vehículo o lo crea si no existe (auto-registration).
     * Los atributos solo se envían en el alta; si el vehículo ya existe se ignoran.
     */
    VehicleInfo getOrCreateVehicle(String plate, VehicleType vehicleType, VehicleAttributes attributes);

    /**
     * Busca el vehículo por matrícula sin crearlo. Vacío si no existe.
     */
    Optional<VehicleInfo> findByPlate(String plate);

    /**
     * Busca el vehículo por id, para resolver la matrícula a mostrar en las
     * consultas de estancias (el dominio de stay no la guarda). Vacío si no existe.
     */
    Optional<VehicleInfo> findById(UUID vehicleId);

    record VehicleInfo(UUID vehicleId, String plate, VehicleType vehicleType, boolean active) {}
}