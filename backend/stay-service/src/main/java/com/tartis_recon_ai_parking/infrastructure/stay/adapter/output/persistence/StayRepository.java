package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StayRepository extends JpaRepository<StayEntity, UUID> {

    boolean existsByVehicleIdAndStatus(UUID vehicleId, StayStatus status);

    Optional<StayEntity> findByVehicleIdAndStatus(UUID vehicleId, StayStatus status);

    Page<StayEntity> findByStatus(StayStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM StayEntity s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:vehicleType IS NULL OR s.vehicleType = :vehicleType) AND " +
           "(:filterByVehicles = false OR s.vehicleId IN :vehicleIds)")
    Page<StayEntity> findByFilters(
            @org.springframework.data.repository.query.Param("status") StayStatus status,
            @org.springframework.data.repository.query.Param("vehicleType") com.tartis_recon_ai_parking.domain.stay.VehicleType vehicleType,
            @org.springframework.data.repository.query.Param("filterByVehicles") boolean filterByVehicles,
            @org.springframework.data.repository.query.Param("vehicleIds") java.util.List<UUID> vehicleIds,
            Pageable pageable);
}
