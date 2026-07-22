package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

/**
 * Entrada del caso de uso de check-in (POST /v1/stays/check-in).
 *
 * <p>La matricula la lee el sensor de entrada; el caso de uso la resuelve a
 * {@code vehicleId} contra vehicle-service. {@code vehicleType} solo es necesario
 * si el vehiculo no esta registrado (auto-registro).
 */
public class StayCreateDTO {

	private final String plate;

	private final VehicleType vehicleType;

	public StayCreateDTO(final String plate, final VehicleType vehicleType) {
		this.plate = plate;
		this.vehicleType = vehicleType;
	}

	public String getPlate() {
		return this.plate;
	}

	public VehicleType getVehicleType() {
		return this.vehicleType;
	}

}
