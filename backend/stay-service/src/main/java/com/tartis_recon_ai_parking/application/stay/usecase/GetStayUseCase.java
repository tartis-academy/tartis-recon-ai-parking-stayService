package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Consulta del detalle de una estancia por id (HU-08). */
public class GetStayUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetStayUseCase.class);

    private final StayPersistence stayPersistence;
    private final StayVehiclePort vehiclePort;
    private final StayDTOFactory stayDTOFactory;

    public GetStayUseCase(StayPersistence stayPersistence, StayVehiclePort vehiclePort,
                          StayDTOFactory stayDTOFactory) {
        this.stayPersistence = stayPersistence;
        this.vehiclePort = vehiclePort;
        this.stayDTOFactory = stayDTOFactory;
    }

    public StayDTO execute(UUID stayId) {
        Stay stay = stayPersistence.findById(stayId)
                .orElseThrow(() -> StayNotFoundException.withId(stayId));

        return stayDTOFactory.create(stay, resolvePlateQuietly(stay.getVehicleId()));
    }

    /**
     * El dominio no guarda la matricula: se resuelve consultando vehicle-service.
     * Es un enriquecimiento de lectura, no una regla de negocio: si vehicle-service
     * falla o el vehiculo no se puede resolver, la consulta no debe romperse, la
     * estancia se devuelve igual con {@code plate} a null.
     */
    private String resolvePlateQuietly(UUID vehicleId) {
        try {
            return vehiclePort.findById(vehicleId)
                    .map(StayVehiclePort.VehicleInfo::plate)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("No se pudo resolver la matricula del vehiculo {} contra vehicle-service", vehicleId, e);
            return null;
        }
    }
}
