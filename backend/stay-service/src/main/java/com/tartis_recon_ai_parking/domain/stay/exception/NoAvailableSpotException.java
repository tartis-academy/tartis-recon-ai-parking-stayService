package com.tartis_recon_ai_parking.domain.stay.exception;
 
/**
 * RN-01: se lanza cuando spot-service informa de que no hay ninguna plaza
 * disponible del tipo solicitado. El check-in debe denegarse.
 *
 * <p>Es la version propia de stay-service: aunque spot-service tiene una
 * excepcion con el mismo proposito, son microservicios distintos y no
 * comparten clases Java entre si.
 */
public class NoAvailableSpotException extends RuntimeException {
 
    public NoAvailableSpotException(String message) {
        super(message);
    }

    public NoAvailableSpotException(String message, Throwable cause) {
        super(message, cause);
    }
}