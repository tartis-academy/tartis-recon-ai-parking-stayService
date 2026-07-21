package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando se intenta construir o modificar una estancia de forma que
 * violaria alguno de sus invariantes de dominio.
 *
 * <p>Cumple el invariante IN-34: cada entidad de dominio valida sus invariantes
 * en construccion y, si los datos son invalidos, lanza {@code Invalid{Dominio}Exception}.
 *
 * <p>La traduccion de esta excepcion a una respuesta HTTP es responsabilidad
 * exclusiva de {@code CustomizedExceptionAdapter} (invariante IN-36).
 */
public class InvalidStayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidStayException(String message) {
        super(message);
    }

    public InvalidStayException(String message, Throwable cause) {
        super(message, cause);
    }
}
