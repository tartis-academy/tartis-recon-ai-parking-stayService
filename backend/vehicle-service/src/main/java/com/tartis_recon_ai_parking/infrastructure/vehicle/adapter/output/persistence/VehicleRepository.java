package com.tartis_recon_ai_parking.infrastructure.vehicle.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<VehicleEntity, Long> {
}
