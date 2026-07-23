package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Salida de los casos de uso de stay (proyeccion del dominio Stay).
 *
 * <p>Referencia al vehiculo por {@code vehicleId} (no por matricula); el dominio
 * no guarda la matricula. {@code checkOut} y {@code totalAmount} son {@code null}
 * mientras la estancia siga en curso (IN-14/IN-16).
 */
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

	public StayDTO(final UUID stayId, final UUID vehicleId, final VehicleType vehicleType,
			final UUID spotId, final UUID tariffId, final Instant checkIn, final Instant checkOut,
			final BigDecimal totalAmount, final StayStatus status) {
		this.stayId = stayId;
		this.vehicleId = vehicleId;
		this.vehicleType = vehicleType;
		this.spotId = spotId;
		this.tariffId = tariffId;
		this.checkIn = checkIn;
		this.checkOut = checkOut;
		this.totalAmount = totalAmount;
		this.status = status;
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

}
