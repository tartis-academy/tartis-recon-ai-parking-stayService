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
     * IN-02 / IN-18: un vehiculo (identificado por matricula) nunca tiene mas
     * de una estancia en curso simultaneamente. El caso de uso de check-in
     * debe comprobar esto antes de crear una Stay nueva (cubre tambien CB-05:
     * denegar acceso si la matricula ya consta activa).
     */
    boolean existsByPlateAndStatus(String plate, StayStatus status);
 
    /**
     * Recupera la estancia activa de una matricula (por ejemplo, para el
     * check-out via matricula en vez de via stayId).
     */
    Optional<Stay> findByPlateAndStatus(String plate, StayStatus status);
}