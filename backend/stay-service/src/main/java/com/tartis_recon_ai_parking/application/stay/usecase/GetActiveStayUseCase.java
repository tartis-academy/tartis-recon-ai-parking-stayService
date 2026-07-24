package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import java.util.UUID;


public class GetActiveStayUseCase {

    private final StayPersistence stayPersistence;
    private final StayDTOFactory stayDTOFactory;

    public GetActiveStayUseCase(StayPersistence stayPersistence, StayDTOFactory stayDTOFactory) {
        this.stayPersistence = stayPersistence;
        this.stayDTOFactory = stayDTOFactory;
    }

    public StayDTO execute(UUID id) {
        Stay stay = stayPersistence.findByVehicleIdAndStatus(id, StayStatus.IN_PROGRESS)
                .or(() -> stayPersistence.findById(id).filter(s -> s.getStatus() == StayStatus.IN_PROGRESS))
                .orElseThrow(() -> StayNotFoundException.activeByVehicleId(id));

        return stayDTOFactory.create(stay);
    }
}
