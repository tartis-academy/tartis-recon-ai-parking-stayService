package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.GetStayUseCase;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckInResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.StayResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada REST de las estancias.
 *
 * <p>No contiene logica de negocio: valida el formato de la peticion, delega en el
 * caso de uso y traduce el resultado. Los errores los traduce a HTTP el
 * {@code CustomizedExceptionAdapter} (IN-36).
 */
@RestController
@RequestMapping("/v1/stays")
public class StayRestAdapter {

    private final CheckInUseCase checkInUseCase;
    private final GetStayUseCase getStayUseCase;
    private final StayRestMapper mapper;

    public StayRestAdapter(CheckInUseCase checkInUseCase, GetStayUseCase getStayUseCase, StayRestMapper mapper) {
        this.checkInUseCase = checkInUseCase;
        this.getStayUseCase = getStayUseCase;
        this.mapper = mapper;
    }

    /**
     * Entrada de vehiculo (HU-01).
     *
     * <p>Un 201 es acceso autorizado: plaza asignada y barrera abierta (CA3).
     * Cualquier error es acceso denegado con la barrera cerrada: 409 parking
     * completo (RN-01, CA-01) o vehiculo ya dentro (CB-05), 422 vehiculo dado de
     * baja (RN-11).
     */
    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody StayRequest request) {
        StayDTO stay = checkInUseCase.execute(mapper.toCreateDTO(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toCheckInResponse(stay, request.plate));
    }

    /** Detalle de una estancia (HU-08). 404 si no existe. */
    @GetMapping("/{stayId}")
    public ResponseEntity<StayResponse> getStay(@PathVariable UUID stayId) {
        StayDTO stay = getStayUseCase.execute(stayId);
        return ResponseEntity.ok(mapper.toStayResponse(stay));
    }
}
