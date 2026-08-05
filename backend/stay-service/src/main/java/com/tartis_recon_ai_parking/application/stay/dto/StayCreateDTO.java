package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;

public class StayCreateDTO {

	private final String plate;

	private final VehicleType vehicleType;

	private final VehicleAttributes vehicleAttributes;

	public StayCreateDTO(final String plate, final VehicleType vehicleType) {
		this(plate, vehicleType, VehicleAttributes.EMPTY);
	}

	public StayCreateDTO(final String plate, final VehicleType vehicleType,
			final VehicleAttributes vehicleAttributes) {
		this.plate = plate;
		this.vehicleType = vehicleType;
		this.vehicleAttributes = vehicleAttributes != null ? vehicleAttributes : VehicleAttributes.EMPTY;
	}

	public String getPlate() {
		return this.plate;
	}

	public VehicleType getVehicleType() {
		return this.vehicleType;
	}

	public VehicleAttributes getVehicleAttributes() {
		return this.vehicleAttributes;
	}

}
