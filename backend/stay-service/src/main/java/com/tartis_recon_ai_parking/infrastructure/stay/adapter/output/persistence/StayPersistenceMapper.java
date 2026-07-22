package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de infraestructura entre el modelo de Dominio (Stay) y el modelo
 * de Base de Datos (StayEntity).
 *
 * <p>toEntity() lo genera MapStruct automaticamente (Stay solo tiene getters).
 * toDomain() se escribe a mano porque Stay no tiene constructor publico:
 * hay que reconstruirla via el factory estatico Stay.restore(), que revalida
 * todos los invariantes de dominio al cargar datos desde la base de datos.
 */
@Mapper(componentModel = "spring")
public interface StayPersistenceMapper {

    @Mapping(source = "id", target = "uniqueId")
    StayEntity toEntity(Stay stay);

    default Stay toDomain(StayEntity entity) {
        if (entity == null) {
            return null;
        }
        return Stay.restore(
                entity.getUniqueId(),
                entity.getPlate(),
                entity.getVehicleType(),
                entity.getSpotId(),
                entity.getTariffId(),
                entity.getCheckIn(),
                entity.getCheckOut(),
                entity.getTotalAmount(),
                entity.getStatus()
        );
    }
}