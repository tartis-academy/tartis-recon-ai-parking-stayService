package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.EntryTicketDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCheckOutDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
import com.tartis_recon_ai_parking.application.stay.dto.VehicleAttributes;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayCheckOutRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckInResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckOutResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.EntryTicketResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.StayPageResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.StayResponse;

import org.springframework.stereotype.Component;

import java.util.List;

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
        return new StayCreateDTO(
                request.plate,
                parseVehicleType(request.vehicleType),
                new VehicleAttributes(request.brand, request.model, request.color));
    }

    /**
     * Construye la respuesta del check-in. {@code plate} viaja en la peticion (el
     * dominio no la guarda); {@code entryTicket} llega ya emitido por ticket-service
     * a traves del caso de uso.
     */
    public CheckInResponse toCheckInResponse(CheckInResultDTO result, String plate) {
        StayDTO dto = result.getStay();
        return new CheckInResponse(
                dto.getStayId(),
                plate,
                dto.getSpotId(),
                dto.getCheckIn(),
                dto.getStatus(),
                toEntryTicketResponse(result.getEntryTicket()));
    }

    private static EntryTicketResponse toEntryTicketResponse(EntryTicketDTO entryTicket) {
        if (entryTicket == null) {
            return null;
        }
        return new EntryTicketResponse(
                entryTicket.getTicketId(),
                entryTicket.getBarCode(),
                entryTicket.getIssuedAt());
    }

    public StayCheckOutDTO toCheckOutDTO(StayCheckOutRequest request) {
        return new StayCheckOutDTO(request.plate, request.entryTicketId);
    }

    public CheckOutResponse toCheckOutResponse(CheckOutResultDTO result, String plate) {
        StayDTO stay = result.getStay();
        return new CheckOutResponse(
                stay.getStayId(),
                plate,
                stay.getCheckIn(),
                stay.getCheckOut(),
                result.getTotalMinutes(),
                stay.getTotalAmount(),
                result.getExitTicketId(),
                stay.getStatus());
    }

    /**
     * {@code plate} viaja resuelta dentro del propio {@code dto} (el caso de uso la
     * obtiene de vehicle-service; el dominio no la guarda).
     */
    public StayResponse toStayResponse(StayDTO dto) {
        return toStayResponse(dto, dto.getPlate());
    }

    public StayResponse toStayResponse(StayDTO dto, String plate) {
        return new StayResponse(
                dto.getStayId(),
                plate,
                dto.getVehicleId(),
                dto.getSpotId(),
                dto.getTariffId(),
                dto.getStatus(),
                dto.getCheckIn(),
                dto.getCheckOut(),
                dto.getTotalAmount());
    }

    /** Traduce la pagina de aplicacion al contrato REST ({@code plate} ya resuelta por el caso de uso). */
    public StayPageResponse toStayPageResponse(StayPageDTO page) {
        List<StayResponse> content = page.getContent().stream()
                .map(this::toStayResponse)
                .toList();
        return new StayPageResponse(
                content,
                page.getPage(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
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
