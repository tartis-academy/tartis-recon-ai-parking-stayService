package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Otra operacion modifico la misma estancia mientras esta la tenia leida, y
 * esta ha perdido la carrera.
 *
 * <p>El caso real es un doble check-out del mismo vehiculo: dos peticiones leen
 * la estancia en IN_PROGRESS, las dos calculan importe contra tariff-service y
 * las dos intentan cerrarla. Sin control de concurrencia la segunda escritura
 * pisaria a la primera (<em>lost update</em>) y, peor todavia, se publicarian
 * dos {@code StayClosedEvent}: ticket-service emitiria dos tickets de salida y
 * spot-service liberaria la plaza dos veces. Con {@code @Version} en
 * {@code StayEntity}, la segunda escritura no encuentra la version que esperaba,
 * Hibernate lanza {@code OptimisticLockingFailureException} y el adaptador de
 * persistencia la traduce a esta excepcion, <b>antes</b> de que se llegue a
 * publicar el evento.
 *
 * <p><b>Se traduce a 409 Conflict</b>, no a 500: no es un fallo del sistema sino
 * un conflicto legitimo entre dos operaciones simultaneas, y la primera de las
 * dos si se completo correctamente. La diferencia practica esta en lo que le
 * dice al cliente: un 409 significa "vuelve a consultar el estado, la operacion
 * probablemente ya esta hecha", mientras que un 500 significaria "algo se ha
 * roto".
 *
 * <p>Distinta de {@link DuplicateActiveStayException}, que cubre la carrera de
 * <em>entrada</em> (dos check-in del mismo vehiculo). Aquella la detecta el
 * indice unico parcial de la base de datos; esta, el contador de version.
 */
public class ConcurrentStayModificationException extends RuntimeException {

    public ConcurrentStayModificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConcurrentStayModificationException(String message) {
        super(message);
    }
}
