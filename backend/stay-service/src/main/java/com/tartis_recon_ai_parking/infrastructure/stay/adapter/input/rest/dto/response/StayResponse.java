package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta con el detalle de una estancia (GET /v1/stays/{id}, listado, cancel).
 * Corresponde al schema {@code StayResponse} del openapi.yml.
 *
 * <p>{@code plate} lo rellena el caso de uso resolviendolo contra vehicle-service
 * a partir de {@code vehicleId}; el dominio no lo almacena.
 */
public class StayResponse {

    private UUID stayId;
    private String plate;
    private UUID vehicleId;
    private UUID spotId;
    private UUID tariffId;
    private StayStatus status;
    private Instant checkIn;
    private Instant checkOut;
    private BigDecimal totalAmount;

    public StayResponse() {
    }

    public StayResponse(UUID stayId, String plate, UUID vehicleId, UUID spotId, UUID tariffId,
                        StayStatus status, Instant checkIn, Instant checkOut, BigDecimal totalAmount) {
        this.stayId = stayId;
        this.plate = plate;
        this.vehicleId = vehicleId;
        this.spotId = spotId;
        this.tariffId = tariffId;
        this.status = status;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalAmount = totalAmount;
    }

    public UUID getStayId() {
        return stayId;
    }

    public void setStayId(UUID stayId) {
        this.stayId = stayId;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
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

    public StayStatus getStatus() {
        return status;
    }

    public void setStatus(StayStatus status) {
        this.status = status;
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
}
