package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StayTicketClientAdapter implements StayTicketPort {

    private final RestClient restClient;
    private static final Logger log = LoggerFactory.getLogger(StayTicketClientAdapter.class);
    private static final String CIRCUIT_BREAKER_NAME = "ticketService";

    public StayTicketClientAdapter(
            RestClient.Builder builder,
            @Value("${services.ticket.url:http://ticket-service:8080}") String ticketServiceUrl) {
        this.restClient = builder.baseUrl(ticketServiceUrl).build();
    }

    /**
     * Espejo de EntryTicketResponse de ticket-service (id, stayId, issuedAt, code).
     *
     * <p>
     * No se deserializa directamente a {@link EntryTicketInfo}: sus componentes
     * se llaman ticketId/barCode, y los de ticket-service id/code. Jackson no falla
     * por eso (ignora lo que no reconoce y deja null lo que no encuentra), asi que
     * antes de este fix el check-in devolvia 201 con entryTicket.ticketId y
     * entryTicket.barCode siempre a null, en silencio, con el ticket ya creado de
     * verdad en la BD de ticket-service. Se detecto probando el flujo end-to-end
     * contra release, no por revision de codigo.
     */
    private record EntryTicketResponse(UUID id, UUID stayId, Instant issuedAt, String code) {
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "issueEntryTicketFallback")
    public EntryTicketInfo issueEntryTicket(UUID stayId, String plate, Instant issuedAt) {
        EntryTicketResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/entry-tickets") // La raíz del recurso sin añadir /entry
                    .body(Map.of(
                            "stayId", stayId,
                            "plate", plate,
                            "issuedAt", issuedAt.toString()))
                    .retrieve()
                    .body(EntryTicketResponse.class);
        } catch (RestClientException e) {
            // ticket-service caido, timeout, 5xx... se traduce para que el frontend
            // reciba un ErrorResponse interpretable en vez de una excepcion de red
            // cruda (IN-36).
            throw new TicketServiceException(
                    "No se pudo contactar con ticket-service para emitir el ticket de entrada de la estancia "
                            + stayId,
                    e);
        }

        if (response == null) {
            // Respuesta 200 pero sin cuerpo: contrato incumplido por ticket-service.
            // Sin esto, un CheckInResultDTO con ticket null explota mas adelante con
            // una NullPointerException cruda al leer ticket.ticketId().
            throw new TicketServiceException(
                    "ticket-service no devolvió el ticket de entrada para la estancia " + stayId);
        }

        return new EntryTicketInfo(response.id(), response.code(), response.issuedAt());
    }

    /**
     * Fallback de contingencia (RES-07): Si ticket-service cae o el circuito está
     * abierto,
     * se genera un ticket offline (degradación suave) para no bloquear la barrera.
     */
    private EntryTicketInfo issueEntryTicketFallback(UUID stayId, String plate, Instant issuedAt, Throwable t) {
        log.warn("ticket-service no disponible ({}). Generando ticket de entrada OFFLINE para matrícula {}",
                t.getClass().getSimpleName(), plate);

        // Generamos un ID y código temporal. El caso de uso lo recibirá y el check-in
        // continuará.
        return new EntryTicketInfo(
                UUID.randomUUID(),
                "OFFLINE-ENTRY-" + plate,
                issuedAt);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "issueExitTicketFallback")
    public UUID issueExitTicket(UUID stayId, UUID entryTicketId, BigDecimal totalAmount) {
        // TicketRequest de ticket-service (POST /v1/tickets) solo acepta stayId y
        // totalAmount;
        // entryTicketId no forma parte de su contrato todavia.
        record ExitTicketRequest(String stayId, BigDecimal totalAmount) {
        }

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri("/v1/tickets")
                    .body(new ExitTicketRequest(stayId.toString(), totalAmount))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new TicketServiceException(
                    "No se pudo contactar con ticket-service para emitir el ticket de salida de la estancia "
                            + stayId,
                    e);
        }

        if (response == null || !response.containsKey("uniqueId")) {
            // Respuesta valida en forma pero incompleta: no hay un caso de negocio
            // legitimo en el que emitir un ticket de salida deba denegarse, asi que
            // esto es siempre un fallo de contrato de ticket-service, no del check-out.
            throw new TicketServiceException(
                    "ticket-service no devolvió el ticket de salida para la estancia " + stayId);
        }

        return UUID.fromString((String) response.get("uniqueId"));
    }

    /**
     * Fallback de contingencia (RES-07): Si ticket-service cae en el check-out,
     * se genera un ticket offline para no bloquear la salida del vehículo.
     */
    private UUID issueExitTicketFallback(UUID stayId, UUID entryTicketId, BigDecimal totalAmount, Throwable t) {
        log.warn("ticket-service no disponible ({}). Generando ticket de salida OFFLINE para estancia {}",
                t.getClass().getSimpleName(), stayId);

        // Generamos un UUID temporal.
        return UUID.randomUUID();
    }
}