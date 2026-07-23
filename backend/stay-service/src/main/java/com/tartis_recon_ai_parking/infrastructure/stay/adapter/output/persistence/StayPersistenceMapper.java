package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
                entity.getVehicleId(),
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
