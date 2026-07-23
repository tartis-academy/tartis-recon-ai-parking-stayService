package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

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
