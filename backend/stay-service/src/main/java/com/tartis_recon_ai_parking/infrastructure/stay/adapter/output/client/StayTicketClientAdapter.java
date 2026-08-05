package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
    public EntryTicketInfo issueEntryTicketFallback(UUID stayId, String plate, Instant issuedAt, Throwable t) throws Throwable {
    if (!shouldDegradeToOffline(t)) {
        throw t;
    }

    // M1: Sufijo único basado en UUID para evitar colisiones y no exponer la matrícula en claro
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    String offlineCode = "OFFLINE-ENTRY-" + uniqueSuffix;
    
    // M2: ticketId local para la estancia degradada
    UUID fallbackTicketId = UUID.randomUUID();

    log.warn("[RECONCILIATION-REQUIRED] RES-07 Fallback activado para stayId={}. ticket-service no disponible ({}). " +
             "Generado ticket offline local ticketId={}, barCode={}.",
             stayId, t.getClass().getSimpleName(), fallbackTicketId, offlineCode);

    return new EntryTicketInfo(fallbackTicketId, offlineCode, issuedAt);
}

    /**
     * Evalúa si la excepción corresponde a un fallo de infraestructura o circuito
     * abierto
     * que justifica la degradación suave a ticket OFFLINE.
     */
    private boolean shouldDegradeToOffline(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return true; // Circuito abierto en Resilience4j
        }
        Throwable rootCause = t.getCause() != null ? t.getCause() : t;

        // Errores 4xx (p. ej. HttpClientErrorException.BadRequest o Conflict) NO deben
        // degradar
        if (rootCause instanceof HttpClientErrorException) {
            return false;
        }

        // Degradamos si es fallo de red/timeout o error 5xx del servidor
        return rootCause instanceof ResourceAccessException
                || rootCause instanceof HttpServerErrorException
                || t instanceof TicketServiceException;
    }

} 


    

    
    

    
    
    
    
    

    