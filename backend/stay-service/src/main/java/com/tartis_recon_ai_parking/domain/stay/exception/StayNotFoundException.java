package com.tartis_recon_ai_parking.domain.stay.exception;

import java.util.UUID;

/**
 * Se lanza cuando se solicita una estancia que no existe en el sistema.
 *
 * <p>Cubre, entre otros, el criterio CA5 de HU-08 (consultar una estancia
 * inexistente informa del error) y CA-02 de HU-02 (matricula sin estancia
 * registrada: se impide la salida y se notifica la incidencia).
 *
 * <p>La traduccion a HTTP 404 corresponde a {@code CustomizedExceptionAdapter}
 * (invariante IN-36).
 */
public class StayNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StayNotFoundException(String message) {
        super(message);
    }

    public static StayNotFoundException withId(UUID id) {
        return new StayNotFoundException("No existe ninguna estancia con id " + id);
    }

    public static StayNotFoundException activeByPlate(String plate) {
        return new StayNotFoundException("No existe ninguna estancia en curso para la matricula " + plate);
    }
}
