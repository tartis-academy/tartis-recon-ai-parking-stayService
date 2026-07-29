package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando vehicle-service no responde, responde con un fallo que no
 * es de negocio (caido, timeout, error 5xx), o devuelve una respuesta valida
 * en forma pero incompleta (sin {@code uniqueId}).
 *
 * <p>El 404 ("matricula no existe todavia") NO es este caso: es una respuesta
 * de negocio legitima que {@code StayVehicleClientAdapter} ya maneja aparte
 * (crea el vehiculo, o devuelve {@code Optional.empty()} segun el metodo).
 * Esta excepcion es solo para lo que no es negocio. La usa
 * {@code StayVehicleClientAdapter} para que nunca llegue sin traducir hasta el
 * frontend una excepcion cruda de red (IN-36).
 */
public class VehicleServiceException extends RuntimeException {

    public VehicleServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public VehicleServiceException(String message) {
        super(message);
    }
}
