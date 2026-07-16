package com.tartis_recon_ai_parking.infrastructure.spot.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotRepository extends JpaRepository<SpotEntity, Long> {
}
