package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventpublisher.event.EntryTicketOfflineEvent;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;

    public StayTicketClientAdapter(
            RestClient.Builder builder,
            RabbitTemplate rabbitTemplate,
            @Value("${services.ticket.url:http://ticket-service:8080}") String ticketServiceUrl) {
        this.restClient = builder.baseUrl(ticketServiceUrl).build();
        this.rabbitTemplate = rabbitTemplate;
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
    public EntryTicketInfo issueEntryTicketFallback(UUID stayId, String plate, Instant issuedAt, Throwable t)
            throws Throwable {
       if (t instanceof HttpClientErrorException || (t.getCause() instanceof HttpClientErrorException)) {
            throw t;
        }

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String offlineCode = "OFFLINE-ENTRY-" + uniqueSuffix;
        UUID fallbackTicketId = UUID.randomUUID();

        log.warn("[RECONCILIATION-REQUIRED] RES-07 Fallback activado para stayId={}. ticket-service no disponible ({}). Generado ticket offline local ticketId={}, barCode={}.",
                 stayId, t.getClass().getSimpleName(), fallbackTicketId, offlineCode);

        // Publicación del evento para reconciliación en ticket-service cuando este se recupere
        try {
            EntryTicketOfflineEvent event = new EntryTicketOfflineEvent(stayId, plate, offlineCode, issuedAt);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY_ENTRY_TICKET_OFFLINE,
                    event
            );
            log.info("[RECONCILIATION-QUEUED] Evento enviado a RabbitMQ para stayId={}", stayId);
        } catch (Exception e) {
            log.error("[RECONCILIATION-PUBLISH-FAILED] No se pudo publicar evento de reconciliación para stayId={}", stayId, e);
        }

        return new EntryTicketInfo(fallbackTicketId, offlineCode, issuedAt);
    }

}
