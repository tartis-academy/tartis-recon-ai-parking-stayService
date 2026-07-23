package com.tartis_recon_ai_parking.domain.stay;

import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    public static Stay checkIn(UUID id,
                               UUID vehicleId,
                               VehicleType vehicleType,
                               UUID spotId,
                               UUID tariffId,
                               Instant checkIn) {
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                null, null, StayStatus.IN_PROGRESS);
    }

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

    public Stay finish(Instant checkOutAt, BigDecimal totalAmount) {
        ensureModifiable("finalizar");
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                required(checkOutAt, "checkOut"), required(totalAmount, "totalAmount"),
                StayStatus.FINISHED);
    }

    public Stay cancel(Instant cancelledAt) {
        ensureModifiable("anular");
        return new Stay(id, vehicleId, vehicleType, spotId, tariffId, checkIn,
                required(cancelledAt, "cancelledAt"), null, StayStatus.CANCELLED);
    }

    public boolean isActive() {
        return status == StayStatus.IN_PROGRESS;
    }

    public long parkedMinutesUntil(Instant until) {
        required(until, "until");
        if (until.isBefore(checkIn)) {
            throw new InvalidStayException(
                    "El instante de calculo no puede ser anterior a la hora de entrada");
        }
        long seconds = Duration.between(checkIn, until).getSeconds();
        return (seconds + 59L) / 60L;
    }

    public long parkedMinutes() {
        if (checkOut == null) {
            throw new InvalidStayException(
                    "La estancia sigue en curso: no tiene hora de salida para calcular los minutos");
        }
        return parkedMinutesUntil(checkOut);
    }

    private void validate() {

        if (status == StayStatus.IN_PROGRESS && checkOut != null) {
            throw new InvalidStayException(
                    "Una estancia en curso no puede tener hora de salida (IN-14)");
        }
        if (status.isTerminal() && checkOut == null) {
            throw new InvalidStayException(
                    "Una estancia " + status + " debe tener hora de salida (IN-14)");
        }

        if (checkOut != null && checkOut.isBefore(checkIn)) {
            throw new InvalidStayException(
                    "La hora de salida no puede ser anterior a la hora de entrada (IN-15)");
        }

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

    public Instant getCheckOut() {
        return checkOut;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public StayStatus getStatus() {
        return status;
    }

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
