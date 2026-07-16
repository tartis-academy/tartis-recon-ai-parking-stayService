package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StayRepository extends JpaRepository<StayEntity, Long> {
}
