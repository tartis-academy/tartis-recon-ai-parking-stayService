package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class StaySpotClientAdapter implements StaySpotPort {

    private final RestClient restClient;

    public StaySpotClientAdapter(
            RestClient.Builder builder,
            @Value("${services.spot.url:http://spot-service:8080}") String spotServiceUrl
    ) {
        this.restClient = builder.baseUrl(spotServiceUrl).build();
    }

    @Override
    public UUID assignSpot(VehicleType vehicleType) {
        // Llama a spot-service para reservar una plaza libre según tipo
        Map<?, ?> response = restClient.post()
                .uri("/v1/spots/assign")
                .body(Map.of("vehicleType", vehicleType.name()))
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("spotId")) {
            throw new IllegalStateException("No hay plazas disponibles para el tipo: " + vehicleType);
        }

        return UUID.fromString((String) response.get("spotId"));
    }

    @Override
    public void releaseSpot(UUID spotId) {
        restClient.post()
                .uri("/v1/spots/{id}/release", spotId)
                .retrieve()
                .toBodilessEntity();
    }
}