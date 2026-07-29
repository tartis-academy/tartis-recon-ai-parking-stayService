package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando ticket-service no responde, responde con un fallo que no es
 * de negocio (caido, timeout, error 5xx), o devuelve una respuesta incompleta
 * que incumple su propio contrato (p. ej. sin {@code uniqueId} al emitir un
 * ticket de salida, o sin cuerpo al emitir uno de entrada).
 *
 * <p>No existe un "caso de negocio" equivalente a {@code NoActiveTariffException}
 * aqui: emitir un ticket no tiene una condicion de denegacion legitima, asi que
 * cualquier fallo al emitirlo es siempre un problema de infraestructura o de
 * contrato, nunca del propio check-in/check-out. {@code StayTicketClientAdapter}
 * la usa para que nunca llegue sin traducir hasta el frontend una excepcion
 * cruda de red (IN-36).
 */
public class TicketServiceException extends RuntimeException {

    public TicketServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public TicketServiceException(String message) {
        super(message);
    }
}
