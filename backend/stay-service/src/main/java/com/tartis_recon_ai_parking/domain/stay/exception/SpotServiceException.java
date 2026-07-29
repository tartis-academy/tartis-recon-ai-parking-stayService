package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando spot-service no responde o responde con un fallo que no es
 * de negocio (caido, timeout, error 5xx, respuesta malformada).
 *
 * <p>A diferencia de {@link NoAvailableSpotException} (RN-01: "no hay plazas",
 * una respuesta valida de negocio), esta excepcion envuelve un fallo de
 * infraestructura. {@code StaySpotClientAdapter} traduce aqui las excepciones
 * crudas de {@code RestClientException} para que nunca lleguen sin traducir
 * hasta el frontend: {@code CustomizedExceptionAdapter} la convierte en un
 * {@code ErrorResponse} interpretable (503) en vez de dejar pasar una
 * excepcion de red generica (IN-36).
 */
public class SpotServiceException extends RuntimeException {

    public SpotServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
