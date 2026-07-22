package com.tartis_recon_ai_parking.application.stay.port.output;
 
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
 
import java.util.List;
import java.util.Optional;
import java.util.UUID;
 
/**
 * Puerto de salida para la persistencia de Stay.
 * Se implementa en infrastructure/, nunca en domain ni application (IN-33).
 */
public interface StayPersistence {
 
    Stay save(Stay stay);
 
    Optional<Stay> findById(UUID id);
 
    List<Stay> findAll();
 
    /**
     * IN-02 / IN-18: un vehiculo (identificado por su vehicleId) nunca tiene mas
     * de una estancia en curso simultaneamente. El caso de uso de check-in
     * debe comprobar esto antes de crear una Stay nueva (cubre tambien CB-05:
     * denegar acceso si el vehiculo ya consta activo).
     */
    boolean existsByVehicleIdAndStatus(UUID vehicleId, StayStatus status);

    /**
     * Recupera la estancia activa de un vehiculo (por ejemplo, para el
     * check-out via vehicleId en vez de via stayId).
     */
    Optional<Stay> findByVehicleIdAndStatus(UUID vehicleId, StayStatus status);
}