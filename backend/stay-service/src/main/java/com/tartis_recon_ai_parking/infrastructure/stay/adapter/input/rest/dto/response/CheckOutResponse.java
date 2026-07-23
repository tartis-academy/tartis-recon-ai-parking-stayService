package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import com.tartis_recon_ai_parking.domain.stay.StayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta del check-out: estancia cerrada, ticket de salida generado y plaza liberada.
 * Corresponde al schema {@code CheckOutResponse} del openapi.yml.
 */
public class CheckOutResponse {

    private UUID stayId;
    private String plate;
    private Instant checkIn;
    private Instant checkOut;
    /** Minutos redondeados hacia arriba, a favor del sistema (RN-06). */
    private Long totalMinutes;
    private BigDecimal amount;
    /** Ticket de salida / recibo de pago. */
    private UUID ticketId;
    private StayStatus status;

    public CheckOutResponse() {
    }

    public CheckOutResponse(UUID stayId, String plate, Instant checkIn, Instant checkOut,
                            Long totalMinutes, BigDecimal amount, UUID ticketId, StayStatus status) {
        this.stayId = stayId;
        this.plate = plate;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalMinutes = totalMinutes;
        this.amount = amount;
        this.ticketId = ticketId;
        this.status = status;
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

    public Instant getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(Instant checkIn) {
        this.checkIn = checkIn;
    }

    public Instant getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(Instant checkOut) {
        this.checkOut = checkOut;
    }

    public Long getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(Long totalMinutes) {
        this.totalMinutes = totalMinutes;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public StayStatus getStatus() {
        return status;
    }

    public void setStatus(StayStatus status) {
        this.status = status;
    }
}
