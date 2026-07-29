package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.GetActiveStayUseCase;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.StayResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequestMapping("/v1/activeStay")
public class ActiveStayRestAdapter {

    private final GetActiveStayUseCase getActiveStayUseCase;
    private final StayRestMapper mapper;

    public ActiveStayRestAdapter(GetActiveStayUseCase getActiveStayUseCase, StayRestMapper mapper) {
        this.getActiveStayUseCase = getActiveStayUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERARIO')")
    public ResponseEntity<StayResponse> getActiveStay(@PathVariable("id") UUID id) {
        StayDTO stay = getActiveStayUseCase.execute(id);
        return ResponseEntity.ok(mapper.toStayResponse(stay));
    }
}
