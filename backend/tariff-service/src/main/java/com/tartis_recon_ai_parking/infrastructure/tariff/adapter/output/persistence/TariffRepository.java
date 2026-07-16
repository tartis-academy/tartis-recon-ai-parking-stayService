package com.tartis_recon_ai_parking.infrastructure.tariff.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffRepository extends JpaRepository<TariffEntity, Long> {
}
