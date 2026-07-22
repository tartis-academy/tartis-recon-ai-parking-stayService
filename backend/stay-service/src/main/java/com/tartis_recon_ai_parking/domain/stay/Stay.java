package com.tartis_recon_ai_parking.domain.stay;

import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa la estancia de un vehiculo en el parking.
 *
 * <p>Es <b>inmutable</b>: las transiciones de estado ({@link #finish} y {@link #cancel})
 * devuelven una nueva instancia en lugar de mutar la actual. Esto garantiza por
 * construccion el invariante IN-19 (una estancia finalizada o anulada es inmutable,
 * append-only, para la trazabilidad de auditoria).
 *
 * <p>No importa nada de Spring, JPA ni HTTP: es Java puro (invariante IN-32).
 *
 * <p>La estancia referencia al vehiculo por su <b>identidad</b> ({@code vehicleId},
 * el UUID que posee vehicle-service), no por su matricula: la matricula la resuelve
 * el sensor de entrada y vehicle-service, y solo vive en el contrato/API. El dominio
 * trabaja siempre con el identificador estable.
 *
 * <p><b>Invariantes garantizados por esta clase</b>
 * <ul>
 *   <li><b>IN-13</b> — toda estancia referencia exactamente un vehiculo (por su id),
 *       una plaza y una tarifa; ninguno puede ser nulo.</li>
 *   <li><b>IN-14</b> — {@code checkOut} es nulo si y solo si el estado es
 *       {@link StayStatus#IN_PROGRESS}.</li>
 *   <li><b>IN-15</b> — si {@code checkOut} esta definido, {@code checkOut >= checkIn}.</li>
 *   <li><b>IN-16</b> — una estancia finalizada tiene siempre un importe {@code > 0}.</li>
 *   <li><b>IN-19</b> — un estado terminal no admite nuevas transiciones.</li>
 *   <li><b>IN-34</b> — la entidad se autovalida en construccion y lanza
 *       {@link InvalidStayException} si los datos son invalidos.</li>
 * </ul>
 *
 * <p><b>Fuera de alcance de la entidad:</b> los invariantes IN-02, IN-03 y IN-18
 * (unicidad de estancia en curso por vehiculo y por plaza) son de ambito global y
 * se verifican en el caso de uso consultando el puerto de persistencia, ya que una
 * entidad aislada no puede conocer al resto.
 */
public final class Stay {

    private final UUID id;
    private final UUID vehicleId;
    private final VehicleType vehicleType;
    private final UUID spotId;
    private final UUID tariffId;
    private final Instant checkIn;
    private final Instant checkOut;
    private final BigDecimal totalAmount;
    private final StayStatus status;

    private Stay(UUID id,
                 UUID vehicleId,
                 VehicleType vehicleType,
                 UUID spotId,
                 UUID tariffId,
                 Instant checkIn,
                 Instant checkOut,
                 BigDecimal totalAmount,
                 StayStatus status) {
        this.id = required(id, "id");
        this.vehicleId = required(vehicleId, "vehicleId");
        this.vehicleType = required(vehicleType, "vehicleType");
        this.spotId = required(spotId, "spotId");
        this.tariffId = required(tariffId, "tariffId");
        this.checkIn = required(checkIn, "checkIn");
        this.status = required(status, "status");
        this.checkOut = checkOut;
        this.totalAmount = totalAmount;
        validate();
    }

    // ------------------------------------------------------------------
    // Fabricas
    // ------------------------------------------------------------------

    /**
     * Crea una estancia nueva en curso, tras un check-in correcto.
     * El vehiculo, la plaza y la tarifa ya deben haber sido resueltos por el caso de uso.
     */
    public static Stay checkIn(UUID id,
                               UUID vehicleId,
                               VehicleType vehicleType,
                               UUID spotId,
                               UUID tariffId,
                               Instant checkIn) {
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                null, null, StayStatus.IN_PROGRESS);
    }

    /**
     * Reconstruye una estancia existente a partir de los datos persistidos.
     * Lo usa el adaptador de persistencia; revalida todos los invariantes,
     * de modo que datos corruptos en base de datos se detectan al cargarlos.
     */
    public static Stay restore(UUID id,
                               UUID vehicleId,
                               VehicleType vehicleType,
                               UUID spotId,
                               UUID tariffId,
                               Instant checkIn,
                               Instant checkOut,
                               BigDecimal totalAmount,
                               StayStatus status) {
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                checkOut, totalAmount, status);
    }

    // ------------------------------------------------------------------
    // Transiciones de estado
    // ------------------------------------------------------------------

    /**
     * Cierra la estancia con su hora de salida e importe calculado.
     *
     * @return una nueva instancia en estado {@link StayStatus#FINISHED}
     * @throws InvalidStayException si la estancia ya estaba en un estado terminal (IN-19),
     *                              si {@code checkOutAt} es anterior al check-in (IN-15)
     *                              o si el importe no es mayor que cero (IN-16)
     */
    public Stay finish(Instant checkOutAt, BigDecimal totalAmount) {
        ensureModifiable("finalizar");
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                required(checkOutAt, "checkOut"), required(totalAmount, "totalAmount"),
                StayStatus.FINISHED);
    }

    /**
     * Anula la estancia sin generar cobro. Cubre casos borde como CB-06
     * (el vehiculo cruza la barrera pero retrocede sin llegar a entrar).
     *
     * @return una nueva instancia en estado {@link StayStatus#CANCELLED}
     * @throws InvalidStayException si la estancia ya estaba en un estado terminal (IN-19)
     */
    public Stay cancel(Instant cancelledAt) {
        ensureModifiable("anular");
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                required(cancelledAt, "cancelledAt"), null, StayStatus.CANCELLED);
    }

    // ------------------------------------------------------------------
    // Comportamiento de dominio
    // ------------------------------------------------------------------

    /** @return true si el vehiculo sigue dentro del parking. */
    public boolean isActive() {
        return status == StayStatus.IN_PROGRESS;
    }

    /**
     * Minutos estacionados hasta el instante indicado, <b>redondeados hacia arriba</b>
     * segun la regla RN-06 ("redondeados a favor del sistema").
     *
     * @throws InvalidStayException si {@code until} es nulo o anterior al check-in
     */
    public long parkedMinutesUntil(Instant until) {
        required(until, "until");
        if (until.isBefore(checkIn)) {
            throw new InvalidStayException(
                    "El instante de calculo no puede ser anterior a la hora de entrada");
        }
        long seconds = Duration.between(checkIn, until).getSeconds();
        return (seconds + 59L) / 60L;
    }

    /**
     * Minutos estacionados de una estancia ya cerrada.
     *
     * @throws InvalidStayException si la estancia sigue en curso
     */
    public long parkedMinutes() {
        if (checkOut == null) {
            throw new InvalidStayException(
                    "La estancia sigue en curso: no tiene hora de salida para calcular los minutos");
        }
        return parkedMinutesUntil(checkOut);
    }

    // ------------------------------------------------------------------
    // Validacion de invariantes (IN-34)
    // ------------------------------------------------------------------

    private void validate() {
        // IN-14: checkOut es nulo si y solo si el estado es IN_PROGRESS
        if (status == StayStatus.IN_PROGRESS && checkOut != null) {
            throw new InvalidStayException(
                    "Una estancia en curso no puede tener hora de salida (IN-14)");
        }
        if (status.isTerminal() && checkOut == null) {
            throw new InvalidStayException(
                    "Una estancia " + status + " debe tener hora de salida (IN-14)");
        }

        // IN-15: coherencia temporal
        if (checkOut != null && checkOut.isBefore(checkIn)) {
            throw new InvalidStayException(
                    "La hora de salida no puede ser anterior a la hora de entrada (IN-15)");
        }

        // IN-16: importe solo en estancias finalizadas y siempre mayor que cero
        if (status == StayStatus.FINISHED) {
            if (totalAmount == null) {
                throw new InvalidStayException(
                        "Una estancia finalizada debe tener importe calculado (IN-16)");
            }
            if (totalAmount.signum() <= 0) {
                throw new InvalidStayException(
                        "El importe de una estancia finalizada debe ser mayor que cero (IN-16)");
            }
        } else if (totalAmount != null) {
            throw new InvalidStayException(
                    "Solo una estancia finalizada puede tener importe calculado (IN-16)");
        }
    }

    /** Garantiza IN-19: los estados terminales no admiten cambios. */
    private void ensureModifiable(String accion) {
        if (status.isTerminal()) {
            throw new InvalidStayException(
                    "No se puede " + accion + " una estancia en estado " + status
                            + ": es inmutable (IN-19)");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidStayException("El campo '" + field + "' es obligatorio");
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Accesores
    // ------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public UUID getSpotId() {
        return spotId;
    }

    public UUID getTariffId() {
        return tariffId;
    }

    public Instant getCheckIn() {
        return checkIn;
    }

    /** @return la hora de salida, o {@code null} si la estancia sigue en curso. */
    public Instant getCheckOut() {
        return checkOut;
    }

    /** @return el importe calculado, o {@code null} si la estancia no esta finalizada. */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public StayStatus getStatus() {
        return status;
    }

    // ------------------------------------------------------------------
    // Identidad
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Stay)) {
            return false;
        }
        return id.equals(((Stay) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Stay{id=" + id
                + ", vehicleId=" + vehicleId
                + ", vehicleType=" + vehicleType
                + ", spotId=" + spotId
                + ", status=" + status
                + '}';
    }
}
