package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * RN-11: se lanza cuando el vehiculo que intenta entrar esta dado de baja
 * ({@code active = false} en vehicle-service). El check-in debe denegarse y la
 * barrera permanecer cerrada.
 *
 * <p>A diferencia de {@link NoAvailableSpotException} (RN-01), aqui el parking
 * puede estar vacio: se deniega por el estado del vehiculo, no por falta de plaza.
 */
public class VehicleDeactivatedException extends RuntimeException {

    public VehicleDeactivatedException(String message) {
        super(message);
    }
}
