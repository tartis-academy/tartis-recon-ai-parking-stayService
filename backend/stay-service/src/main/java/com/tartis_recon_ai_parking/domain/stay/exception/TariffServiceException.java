package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando tariff-service no responde, responde con un fallo que no es
 * de negocio (caido, timeout, error 5xx), o devuelve una respuesta valida en
 * forma pero incompleta (p. ej. {@code /v1/tariffs/calculate} sin importe).
 *
 * <p>A diferencia de {@link NoActiveTariffException} (IN-08: "no hay tarifa
 * activa", una respuesta de negocio bien formada), esta excepcion envuelve un
 * fallo de infraestructura o un contrato incumplido. {@code StayTariffClientAdapter}
 * la usa para que nunca llegue sin traducir hasta el frontend una excepcion
 * cruda de red (IN-36).
 */
public class TariffServiceException extends RuntimeException {

    public TariffServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public TariffServiceException(String message) {
        super(message);
    }
}
