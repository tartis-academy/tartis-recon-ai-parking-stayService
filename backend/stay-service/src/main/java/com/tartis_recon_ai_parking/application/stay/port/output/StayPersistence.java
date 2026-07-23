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
}
