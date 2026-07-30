package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/** Listado paginado de estancias con filtro opcional por estado (HU-08, admin). */
public class ListStaysUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListStaysUseCase.class);

    private final StayPersistence stayPersistence;
    private final StayVehiclePort vehiclePort;
    private final StayDTOFactory stayDTOFactory;

    public ListStaysUseCase(StayPersistence stayPersistence, StayVehiclePort vehiclePort,
                            StayDTOFactory stayDTOFactory) {
        this.stayPersistence = stayPersistence;
        this.vehiclePort = vehiclePort;
        this.stayDTOFactory = stayDTOFactory;
    }

    public StayPageDTO execute(StayStatus status, VehicleType vehicleType, String searchPlate, int page, int size) {
        List<UUID> vehicleIds = null;
        if (searchPlate != null && !searchPlate.isBlank()) {
            vehicleIds = vehiclePort.findVehicleIdsByPlateContaining(searchPlate);
        }

        StayPersistence.StayPage result = stayPersistence.findPage(status, vehicleType, vehicleIds, page, size);

        // N+1 deliberado: una llamada a vehicle-service por estancia de la pagina
        // (acotado por "size", 20 por defecto). Aceptable mientras no haya un
        // endpoint de resolucion en lote en vehicle-service; si el listado se usa
        // con paginas grandes o mucho trafico, esto habria que revisitarlo (cache
        // o resolucion batch) en vez de escalar el timeout.
        List<StayDTO> content = result.content().stream()
                .map(stay -> stayDTOFactory.create(stay, resolvePlateQuietly(stay.getVehicleId())))
                .toList();

        return new StayPageDTO(content, result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }

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
