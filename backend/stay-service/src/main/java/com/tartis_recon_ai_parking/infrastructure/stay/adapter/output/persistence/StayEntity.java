package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que mapea la tabla de estancias en PostgreSQL (Stay DB).
 * Vive unicamente en infrastructure (IN-32).
 */
@Entity
@Table(name = "stays")
public class StayEntity {

    @Id
    @Column(name = "unique_id", nullable = false, updatable = false)
    private UUID uniqueId;

    @Column(name = "plate", nullable = false, length = 15)
    private String plate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "spot_id", nullable = false)
    private UUID spotId;

    @Column(name = "tariff_id", nullable = false)
    private UUID tariffId;

    @Column(name = "check_in", nullable = false)
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StayStatus status;

    protected StayEntity() {
        // Requerido por JPA/Hibernate.
    }

    public StayEntity(UUID uniqueId, String plate, VehicleType vehicleType, UUID spotId,UUID tariffId, Instant checkIn, Instant checkOut,BigDecimal totalAmount, StayStatus status) {
        this.uniqueId = uniqueId;
        this.plate = plate;
        this.vehicleType = vehicleType;
        this.spotId = spotId;
        this.tariffId = tariffId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public UUID getSpotId() {
        return spotId;
    }

    public void setSpotId(UUID spotId) {
        this.spotId = spotId;
    }

    public UUID getTariffId() {
        return tariffId;
    }

    public void setTariffId(UUID tariffId) {
        this.tariffId = tariffId;
    }

    public Instant getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(Instant checkIn) {
        this.checkIn = checkIn;
    }

    public Instant getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(Instant checkOut) {
        this.checkOut = checkOut;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public StayStatus getStatus() {
        return status;
    }

    public void setStatus(StayStatus status) {
        this.status = status;
    }
}
