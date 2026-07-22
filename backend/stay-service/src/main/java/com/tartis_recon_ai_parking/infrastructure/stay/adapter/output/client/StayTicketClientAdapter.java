package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StayTicketClientAdapter implements StayTicketPort {

    private final RestClient restClient;

    public StayTicketClientAdapter(
            RestClient.Builder builder,
            @Value("${services.ticket.url:http://ticket-service:8080}") String ticketServiceUrl
    ) {
        this.restClient = builder.baseUrl(ticketServiceUrl).build();
    }

    @Override
public EntryTicketInfo issueEntryTicket(UUID stayId, String plate, Instant issuedAt) {
    return restClient.post()
            .uri("/v1/entry-tickets") // La raíz del recurso sin añadir /entry
            .body(Map.of(
                    "stayId", stayId,
                    "plate", plate,
                    "issuedAt", issuedAt.toString()
            ))
            .retrieve()
            .body(EntryTicketInfo.class);
}

    @Override
    public UUID issueExitTicket(UUID stayId, UUID entryTicketId) {
        Map<?, ?> response = restClient.post()
                .uri("/v1/tickets/exit")
                .body(Map.of(
                        "stayId", stayId.toString(),
                        "entryTicketId", entryTicketId != null ? entryTicketId.toString() : ""
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("exitTicketId")) {
            throw new IllegalStateException("Error al generar el ticket de salida");
        }

        return UUID.fromString((String) response.get("exitTicketId"));
    }
}