package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class StayDTO {

	private final UUID stayId;

	private final UUID vehicleId;

	private final VehicleType vehicleType;

	private final UUID spotId;

	private final UUID tariffId;

	private final Instant checkIn;

	private final Instant checkOut;

	private final BigDecimal totalAmount;

	private final StayStatus status;

	private final String plate;

	/**
	 * El dominio {@code Stay} no guarda la matricula (solo {@code vehicleId}); este
	 * constructor la deja a null para los llamantes que no la resuelven.
	 */
	public StayDTO(final UUID stayId, final UUID vehicleId, final VehicleType vehicleType,
			final UUID spotId, final UUID tariffId, final Instant checkIn, final Instant checkOut,
			final BigDecimal totalAmount, final StayStatus status) {
		this(stayId, vehicleId, vehicleType, spotId, tariffId, checkIn, checkOut, totalAmount, status, null);
	}

	/**
	 * Con {@code plate} ya resuelta (p.ej. por consulta a vehicle-service) para las
	 * respuestas de detalle/listado de estancias.
	 */
	public StayDTO(final UUID stayId, final UUID vehicleId, final VehicleType vehicleType,
			final UUID spotId, final UUID tariffId, final Instant checkIn, final Instant checkOut,
			final BigDecimal totalAmount, final StayStatus status, final String plate) {
		this.stayId = stayId;
		this.vehicleId = vehicleId;
		this.vehicleType = vehicleType;
		this.spotId = spotId;
		this.tariffId = tariffId;
		this.checkIn = checkIn;
		this.checkOut = checkOut;
		this.totalAmount = totalAmount;
		this.status = status;
		this.plate = plate;
	}

	public UUID getStayId() {
		return this.stayId;
	}

	public UUID getVehicleId() {
		return this.vehicleId;
	}

	public VehicleType getVehicleType() {
		return this.vehicleType;
	}

	public UUID getSpotId() {
		return this.spotId;
	}

	public UUID getTariffId() {
		return this.tariffId;
	}

	public Instant getCheckIn() {
		return this.checkIn;
	}

	public Instant getCheckOut() {
		return this.checkOut;
	}

	public BigDecimal getTotalAmount() {
		return this.totalAmount;
	}

	public StayStatus getStatus() {
		return this.status;
	}

	public String getPlate() {
		return this.plate;
	}

}
