package com.tartis_recon_ai_parking.domain.stay.exception;

import java.util.UUID;

public class StayNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StayNotFoundException(String message) {
        super(message);
    }

    public static StayNotFoundException withId(UUID id) {
        return new StayNotFoundException("No existe ninguna estancia con id " + id);
    }

    public static StayNotFoundException activeByVehicleId(UUID vehicleId) {
        return new StayNotFoundException("No existe ninguna estancia en curso para el vehiculo " + vehicleId);
    }
}
