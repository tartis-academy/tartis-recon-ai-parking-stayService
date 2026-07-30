package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StayPersistence {

    Stay save(Stay stay);

    Optional<Stay> findById(UUID id);

    List<Stay> findAll();

    boolean existsByVehicleIdAndStatus(UUID vehicleId, StayStatus status);

    Optional<Stay> findByVehicleIdAndStatus(UUID vehicleId, StayStatus status);

    /**
     * Lista paginada de estancias (HU-08). Si {@code status} es null no filtra por
     * estado. La paginacion se expresa con tipos propios para no filtrar detalles
     * de Spring Data hacia la capa de aplicacion.
     */
    StayPage findPage(StayStatus status, com.tartis_recon_ai_parking.domain.stay.VehicleType vehicleType, List<UUID> vehicleIds, int page, int size);

    record StayPage(List<Stay> content, int page, int size, long totalElements, int totalPages) {
    }
}
