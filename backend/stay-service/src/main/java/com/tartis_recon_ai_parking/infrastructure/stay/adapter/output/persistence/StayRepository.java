package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para StayEntity.
 * existsByPlateAndStatus / findByPlateAndStatus se generan por Spring Data
 * a partir del nombre del metodo (query derivation), sin necesidad de @Query.
 */
public interface StayRepository extends JpaRepository<StayEntity, UUID> {

    boolean existsByPlateAndStatus(String plate, StayStatus status);

    Optional<StayEntity> findByPlateAndStatus(String plate, StayStatus status);
}
