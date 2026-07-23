package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta del check-in: estancia creada, plaza asignada y ticket de entrada emitido.
 * Corresponde al schema {@code CheckInResponse} del openapi.yml.
 */
public class CheckInResponse {

    private UUID stayId;
    private String plate;
    private UUID spotId;
    private Instant checkIn;
    private StayStatus status;
    private EntryTicketResponse entryTicket;

    public CheckInResponse() {
        // Requerido para la deserializacion (Jackson)
    }

    public CheckInResponse(UUID stayId, String plate, UUID spotId, Instant checkIn,
                           StayStatus status, EntryTicketResponse entryTicket) {
        this.stayId = stayId;
        this.plate = plate;
        this.spotId = spotId;
        this.checkIn = checkIn;
        this.status = status;
        this.entryTicket = entryTicket;
    }

    public UUID getStayId() {
        return stayId;
    }

    public void setStayId(UUID stayId) {
        this.stayId = stayId;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public UUID getSpotId() {
        return spotId;
    }

    public void setSpotId(UUID spotId) {
        this.spotId = spotId;
    }

    public Instant getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(Instant checkIn) {
        this.checkIn = checkIn;
    }

    public StayStatus getStatus() {
        return status;
    }

    public void setStatus(StayStatus status) {
        this.status = status;
    }

    public EntryTicketResponse getEntryTicket() {
        return entryTicket;
    }

    public void setEntryTicket(EntryTicketResponse entryTicket) {
        this.entryTicket = entryTicket;
    }
}
