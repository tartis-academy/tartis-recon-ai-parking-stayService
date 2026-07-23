package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando tariff-service no devuelve ninguna tarifa activa (IN-08)
 * para el tipo de vehiculo solicitado. Sin tarifa no se puede completar el
 * check-in, porque Stay.checkIn() exige un tariffId valido.
 */
public class NoActiveTariffException extends RuntimeException {

    public NoActiveTariffException(String message) {
        super(message);
    }
}