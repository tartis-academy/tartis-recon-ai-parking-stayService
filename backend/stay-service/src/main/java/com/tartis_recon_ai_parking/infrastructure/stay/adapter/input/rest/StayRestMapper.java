package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckInResponse;

import org.springframework.stereotype.Component;

/**
 * Traduce entre los DTOs HTTP del check-in y los DTOs de la capa de aplicacion,
 * para que un cambio en el contrato REST no obligue a tocar el caso de uso (IN-31).
 */
@Component
public class StayRestMapper {

    /**
     * {@link StayRequest} lleva el tipo como {@code String} (campo opcional del
     * contrato); el dominio trabaja con el enum {@link VehicleType}. Un tipo
     * presente pero no reconocido es una peticion invalida (400), no un fallo interno.
     */
    public StayCreateDTO toCreateDTO(StayRequest request) {
        return new StayCreateDTO(request.plate, parseVehicleType(request.vehicleType));
    }

    /**
     * Construye la respuesta del check-in. {@code plate} viaja en la peticion (el
     * dominio no la guarda) y {@code entryTicket} queda a null: la emision del ticket
     * de entrada pertenece a la integracion con ticket-service, fuera de este alcance.
     */
    public CheckInResponse toCheckInResponse(StayDTO dto, String plate) {
        return new CheckInResponse(
                dto.getStayId(),
                plate,
                dto.getSpotId(),
                dto.getCheckIn(),
                dto.getStatus(),
                null);
    }

    private static VehicleType parseVehicleType(String raw) {
        if (raw == null || raw.isBlank()) {
            // Ausente: se resuelve el tipo desde vehicle-service si el vehiculo existe.
            return null;
        }
        try {
            return VehicleType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidStayException("Tipo de vehiculo no reconocido: " + raw);
        }
    }
}
