package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code stays}.
 *
 * <p>El indice declarado aqui es el que Hibernate crea cuando genera el esquema
 * el mismo ({@code ddl-auto=create-drop}, que es lo que usan los tests con H2).
 * En demo y produccion el esquema lo lleva Flyway, y ahi la garantia fuerte es
 * el <b>indice unico parcial</b> de {@code V2__race_conditions.sql}
 * ({@code UNIQUE (vehicle_id) WHERE status = 'IN_PROGRESS'}), que JPA no sabe
 * expresar: {@code @Table(uniqueConstraints = ...)} no admite condicion, y sin
 * condicion la unicidad impediria que un vehiculo volviera a entrar despues de
 * su primera estancia.
 */
@Entity
@Table(name = "stays", indexes = {
        @Index(name = "ix_stays_vehicle_id_status", columnList = "vehicle_id, status")
})
public class StayEntity {

    @Id
    @Column(name = "unique_id", nullable = false, updatable = false)
    private UUID uniqueId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "spot_id", nullable = false)
    private UUID spotId;

    @Column(name = "tariff_id", nullable = false)
    private UUID tariffId;

    @Column(name = "check_in", nullable = false)
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StayStatus status;

    /**
     * Contador de concurrencia optimista. Hibernate lo incrementa en cada UPDATE
     * y anade {@code AND version = ?} al WHERE; si otra transaccion se adelanto,
     * el UPDATE afecta a 0 filas y salta {@code OptimisticLockingFailureException}
     * en vez de pisar el cambio ajeno.
     *
     * <p>Es {@code Long} y no {@code long} a proposito. Spring Data usa
     * "{@code version == null}" como senal de "entidad nueva" para decidir entre
     * INSERT y UPDATE, porque el id de esta tabla lo genera el dominio y no la
     * base de datos, asi que no puede usar "id == null" como hace normalmente.
     * Con un {@code long} primitivo, que nunca es null, cada guardado se veria
     * como un alta y acabaria chocando contra la clave primaria.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StayEntity() {
        // Requerido por JPA/Hibernate
    }

    /**
     * Unico constructor publico, y tiene que seguir siendolo.
     *
     * <p>MapStruct elige constructor asi: si hay uno solo publico, usa ese y
     * asigna por setter las propiedades que no esten entre sus parametros (aqui,
     * {@code version}). Anadir un segundo constructor publico rompe esa regla y
     * obliga a desambiguar con {@code @org.mapstruct.Default}, es decir, a meter
     * una anotacion de la libreria de mapeo dentro de una entidad JPA.
     *
     * <p>La version no va como parametro a proposito: la gestiona Hibernate y el
     * mapper la asigna con {@link #setVersion(Long)}. Una fila nueva sale con
     * version null, que es lo que hace que Spring Data la trate como INSERT.
     */
    public StayEntity(UUID uniqueId, UUID vehicleId, VehicleType vehicleType, UUID spotId,UUID tariffId, Instant checkIn, Instant checkOut,BigDecimal totalAmount, StayStatus status) {
        this.uniqueId = uniqueId;
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.spotId = spotId;
        this.tariffId = tariffId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public UUID getSpotId() {
        return spotId;
    }

    public void setSpotId(UUID spotId) {
        this.spotId = spotId;
    }

    public UUID getTariffId() {
        return tariffId;
    }

    public void setTariffId(UUID tariffId) {
        this.tariffId = tariffId;
    }

    public Instant getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(Instant checkIn) {
        this.checkIn = checkIn;
    }

    public Instant getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(Instant checkOut) {
        this.checkOut = checkOut;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public StayStatus getStatus() {
        return status;
    }

    public void setStatus(StayStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Lo gestiona Hibernate. El setter existe solo para que el mapper pueda
     * devolver a la entidad la version que se leyo. No conviene tocarlo a mano:
     * poner una version inventada equivale a decirle a la base de datos
     * "sobrescribe lo que haya", que es justo lo que este mecanismo evita.
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
