package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementacion del puerto de salida StayPersistence (IN-33).
 * Traduce entre el modelo de dominio Stay y la entidad JPA StayEntity a
 * traves de StayPersistenceMapper, y delega en StayRepository.
 */
@Component
public class StayPersistenceAdapter implements StayPersistence {

    private final StayRepository repository;
    private final StayPersistenceMapper mapper;

    public StayPersistenceAdapter(StayRepository repository, StayPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Stay save(Stay stay) {
        StayEntity saved = repository.save(mapper.toEntity(stay));
        return mapper.toDomain(saved);
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
}