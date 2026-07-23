package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckOutUseCase;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayCheckOutRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckInResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckOutResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final CheckOutUseCase checkOutUseCase;
    private final StayRestMapper mapper;

    public StayRestAdapter(CheckInUseCase checkInUseCase, CheckOutUseCase checkOutUseCase, StayRestMapper mapper) {
        this.checkInUseCase = checkInUseCase;
        this.checkOutUseCase = checkOutUseCase;
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

    @PostMapping("/check-out")
    public ResponseEntity<CheckOutResponse> checkOut(@RequestBody StayCheckOutRequest request) {
        CheckOutResultDTO result = checkOutUseCase.execute(mapper.toCheckOutDTO(request));
        return ResponseEntity.ok(mapper.toCheckOutResponse(result, request.plate));
    }
}
