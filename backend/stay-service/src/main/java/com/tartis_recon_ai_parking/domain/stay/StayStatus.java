package com.tartis_recon_ai_parking.domain.stay;

/**
 * Estados posibles de una estancia. Coincide con el enum StayStatus del contrato
 * {@code openapi.yml}: {@code [IN_PROGRESS, PAY_PENDING, PAID, FINISHED, CANCELLED]}.
 *
 * <p>Transiciones validas:
 * <pre>
 *   IN_PROGRESS --finish()--> FINISHED
 *   IN_PROGRESS --cancel()--> CANCELLED
 * </pre>
 * FINISHED y CANCELLED son estados terminales: una estancia en uno de ellos
 * es inmutable (invariante IN-19, append-only para trazabilidad de auditoria).
 *
 * <p>{@link #PAY_PENDING} y {@link #PAID} son estados <b>transitorios</b> del flujo
 * de pago: en Fase 1 el check-out es una unica llamada y el pago se resuelve
 * internamente, por lo que no llegan a persistirse. Se mantienen en el enum para
 * cubrir el contrato y el flujo de pago de Fase 2. No son terminales: una estancia
 * en uno de ellos todavia admite transiciones.
 */
public enum StayStatus {

    /** La estancia esta en curso: el vehiculo sigue dentro del parking. */
    IN_PROGRESS,

    /** Transitorio (Fase 2): el check-out se ha iniciado y el pago esta pendiente. */
    PAY_PENDING,

    /** Transitorio (Fase 2): el pago se ha confirmado, previo al cierre de la estancia. */
    PAID,

    /** La estancia se cerro correctamente con su importe calculado. */
    FINISHED,

    /** La estancia se anulo (p. ej. CB-06: el vehiculo retrocedio sin llegar a entrar). */
    CANCELLED;

    /** @return true si es un estado terminal y por tanto la estancia ya no admite cambios. */
    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
