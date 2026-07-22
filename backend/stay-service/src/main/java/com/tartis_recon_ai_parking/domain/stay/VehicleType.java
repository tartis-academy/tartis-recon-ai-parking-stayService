package com.tartis_recon_ai_parking.domain.stay;

/**
 * Tipos de vehiculo admitidos en el check-in.
 *
 * <p>Corresponde al campo {@code vehicleType} del contrato
 * {@code POST /v1/stays/check-in}, que acepta CAR, CAR_PMR y MOTORBIKE.
 *
 * <p>Cada microservicio es dueño de su propio modelo, por lo que este enum
 * vive en el dominio de stay-service y no se comparte como libreria comun.
 */
public enum VehicleType {

    /** Turismo estandar. */
    CAR,

    /** Turismo con plaza reservada para personas de movilidad reducida. */
    CAR_PMR,

    /** Motocicleta. */
    MOTORBIKE
}
