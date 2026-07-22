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
public UUID occupySpot(VehicleType vehicleType) {
    Map<String, Object> response = restClient.patch()
            .uri("/v1/spots/occupy") 
            .body(Map.of("type", vehicleType.name()))
            .retrieve()
            .body(Map.class);


    if (response == null || !response.containsKey("id")) {
        throw new IllegalStateException("No hay plazas disponibles o respuesta inválida del servicio de plazas");
    }

    return UUID.fromString(response.get("id").toString());
}

    @Override
    public void releaseSpot(UUID spotId) {
        restClient.post()
                .uri("/v1/spots/{id}/release", spotId)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void updateSpotStatus(UUID spotId, String vehicleType) {
    restClient.patch()
            .uri("/v1/spots/{id}/status", spotId)
            .body(Map.of("type", vehicleType)) // Ajusta las claves según SpotRequest
            .retrieve()
            .toBodilessEntity();
}
}