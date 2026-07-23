package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;

import java.util.UUID;

/** Consulta del detalle de una estancia por id (HU-08). */
public class GetStayUseCase {

    private final StayPersistence stayPersistence;
    private final StayDTOFactory stayDTOFactory;

    public GetStayUseCase(StayPersistence stayPersistence, StayDTOFactory stayDTOFactory) {
        this.stayPersistence = stayPersistence;
        this.stayDTOFactory = stayDTOFactory;
    }

    public StayDTO execute(UUID stayId) {
        Stay stay = stayPersistence.findById(stayId)
                .orElseThrow(() -> StayNotFoundException.withId(stayId));
        return stayDTOFactory.create(stay);
    }
}
