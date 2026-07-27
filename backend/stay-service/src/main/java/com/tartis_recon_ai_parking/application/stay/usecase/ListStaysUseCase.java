package com.tartis_recon_ai_parking.application.stay.usecase;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;

import java.util.List;

/** Listado paginado de estancias con filtro opcional por estado (HU-08, admin). */
public class ListStaysUseCase {

    private final StayPersistence stayPersistence;
    private final StayDTOFactory stayDTOFactory;

    public ListStaysUseCase(StayPersistence stayPersistence, StayDTOFactory stayDTOFactory) {
        this.stayPersistence = stayPersistence;
        this.stayDTOFactory = stayDTOFactory;
    }

    public StayPageDTO execute(StayStatus status, int page, int size) {
        StayPersistence.StayPage result = stayPersistence.findPage(status, page, size);

        List<StayDTO> content = stayDTOFactory.create(result.content());

        return new StayPageDTO(content, result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }
}
