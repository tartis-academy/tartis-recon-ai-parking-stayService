package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para StayEntity.
 * existsByVehicleIdAndStatus / findByVehicleIdAndStatus se generan por Spring Data
 * a partir del nombre del metodo (query derivation), sin necesidad de @Query.
 */
public interface StayRepository extends JpaRepository<StayEntity, UUID> {

    boolean existsByVehicleIdAndStatus(UUID vehicleId, StayStatus status);

    Optional<StayEntity> findByVehicleIdAndStatus(UUID vehicleId, StayStatus status);
}
