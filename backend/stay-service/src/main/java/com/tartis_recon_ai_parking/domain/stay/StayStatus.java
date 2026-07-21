package com.tartis_recon_ai_parking.domain.stay;

/**
 * Estados posibles de una estancia.
 *
 * <p>Transiciones validas:
 * <pre>
 *   IN_PROGRESS --finish()--> FINISHED
 *   IN_PROGRESS --cancel()--> CANCELLED
 * </pre>
 * FINISHED y CANCELLED son estados terminales: una estancia en uno de ellos
 * es inmutable (invariante IN-19, append-only para trazabilidad de auditoria).
 */
public enum StayStatus {

    /** La estancia esta en curso: el vehiculo sigue dentro del parking. */
    IN_PROGRESS,

    /** La estancia se cerro correctamente con su importe calculado. */
    FINISHED,

    /** La estancia se anulo (p. ej. CB-06: el vehiculo retrocedio sin llegar a entrar). */
    CANCELLED;

    /** @return true si es un estado terminal y por tanto la estancia ya no admite cambios. */
    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }
}
