package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    public StayPage findPage(StayStatus status, com.tartis_recon_ai_parking.domain.stay.VehicleType vehicleType, List<UUID> vehicleIds, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkIn"));

        boolean filterByVehicles = vehicleIds != null;
        List<UUID> idsToSearch = filterByVehicles && !vehicleIds.isEmpty() ? vehicleIds : List.of(UUID.randomUUID()); // Si es vacia pero debemos filtrar, mandamos un id dummy para que devuelva vacio, pero IN requiere una lista con al menos 1 elemento, asi que le mandamos random si no hay resultados y filterByVehicles=true. Si no hay filter, mandamos null.
        if(filterByVehicles && vehicleIds.isEmpty()){
             // If we need to filter by vehicle but no vehicles matched, return empty page
             return new StayPage(List.of(), page, size, 0, 0);
        }

        Page<StayEntity> result = repository.findByFilters(status, vehicleType, filterByVehicles, idsToSearch, pageable);

        List<Stay> content = result.getContent().stream()
                .map(mapper::toDomain)
                .toList();

        return new StayPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
