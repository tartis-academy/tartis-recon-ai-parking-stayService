package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.exception.ConcurrentStayModificationException;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia.
 *
 * <p>Ademas de guardar y leer, es el punto donde las defensas de concurrencia de
 * la base de datos se traducen a excepciones de dominio: ni los casos de uso ni
 * el dominio deben saber que existe un indice unico o una columna
 * {@code version} (IN-33, IN-35).
 */
@Component
public class StayPersistenceAdapter implements StayPersistence {

    /**
     * Nombre del indice unico parcial creado en {@code V2__race_conditions.sql}.
     *
     * <p>Se comprueba por nombre para no confundir <em>esta</em> violacion de
     * integridad concreta (dos estancias en curso para el mismo vehiculo) con
     * cualquier otra que pudiera aparecer. Un NOT NULL incumplido, por ejemplo,
     * es un bug nuestro y debe seguir subiendo como 500; disfrazarlo de
     * conflicto de negocio lo dejaria escondido para siempre.
     */
    private static final String ACTIVE_STAY_INDEX = "ux_stays_one_active_per_vehicle";

    private final StayRepository repository;
    private final StayPersistenceMapper mapper;

    public StayPersistenceAdapter(StayRepository repository, StayPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Guarda la estancia y convierte los conflictos de concurrencia en
     * excepciones de dominio.
     *
     * <p><b>Por que {@code saveAndFlush} y no {@code save}.</b> Con
     * {@code save()}, Hibernate puede retrasar el INSERT/UPDATE hasta el commit,
     * que ocurre <em>despues</em> de que este metodo haya devuelto: la excepcion
     * saldria fuera del {@code try} y llegaria al cliente sin traducir, como un
     * 500 generico. {@code saveAndFlush} fuerza el SQL aqui dentro, que es donde
     * podemos interpretarlo.
     *
     * <p><b>Por que la transaccion es tan corta.</b> Cubre solo la escritura y
     * no envuelve al caso de uso, que hace llamadas HTTP a spot, tariff y
     * ticket. Mantener una transaccion abierta (y sus bloqueos de fila) durante
     * una llamada de red es la forma habitual de convertir una carrera poco
     * probable en un cuello de botella permanente: con spot-service lento, cada
     * check-in retendria su conexion del pool durante segundos.
     */
    @Override
    @Transactional
    public Stay save(Stay stay) {
        try {
            StayEntity saved = repository.saveAndFlush(mapper.toEntity(stay));
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            // Perdio la carrera de ENTRADA: otro check-in del mismo vehiculo se
            // colo entre nuestro existsByVehicleIdAndStatus y este INSERT. El
            // indice unico parcial de la BD es el unico sitio donde esto se
            // puede detectar de forma fiable habiendo varias replicas.
            if (violatesActiveStayIndex(e)) {
                throw new DuplicateActiveStayException(
                        "El vehiculo " + stay.getVehicleId() + " ya tiene una estancia en curso:"
                                + " otra operacion se adelanto por milisegundos (IN-02, CB-05)");
            }
            throw e;

        } catch (OptimisticLockingFailureException e) {
            // Perdio la carrera de SALIDA: la fila cambio despues de que la
            // leyeramos. Sin esto, la escritura habria pisado el cierre ajeno y
            // se habria publicado un segundo StayClosedEvent.
            throw new ConcurrentStayModificationException(
                    "La estancia " + stay.getId() + " fue modificada por otra operacion simultanea:"
                            + " se descarta este cambio para no sobrescribirla", e);
        }
    }

    /**
     * Recorre la cadena de causas buscando el nombre del indice.
     *
     * <p>Se busca en el texto y no por tipo de excepcion porque cada driver y
     * cada version de Hibernate colocan el nombre de la restriccion en un punto
     * distinto de la cadena. La guarda del {@code cause == current} evita un
     * bucle infinito si alguna excepcion se declara a si misma como causa.
     */
    private static boolean violatesActiveStayIndex(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(ACTIVE_STAY_INDEX)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    @Override
    public Optional<Stay> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Stay> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByVehicleIdAndStatus(UUID vehicleId, StayStatus status) {
        return repository.existsByVehicleIdAndStatus(vehicleId, status);
    }

    @Override
    public Optional<Stay> findByVehicleIdAndStatus(UUID vehicleId, StayStatus status) {
        return repository.findByVehicleIdAndStatus(vehicleId, status).map(mapper::toDomain);
    }

    @Override
    public StayPage findPage(StayStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkIn"));

        Page<StayEntity> result = (status == null)
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);

        List<Stay> content = result.getContent().stream()
                .map(mapper::toDomain)
                .toList();

        return new StayPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
