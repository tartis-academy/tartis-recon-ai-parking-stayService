package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.SpotResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    SpotResponse response;
    try {
        response = restClient.post()
                .uri("/v1/spots/occupy")
                .body(Map.of("vehicleType", vehicleType.name()))
                .retrieve()
                .body(SpotResponse.class); // <-- Uso del DTO limpia los warnings
    } catch (HttpClientErrorException.Conflict e) {
        // RN-01: confirmado contra el openapi.yml y el codigo de spot-service
        // (OccupySpotUseCase / CustomizedExceptionAdapter): "sin plazas" del
        // tipo solicitado se modela siempre como 409, nunca como 200 con id
        // null. Es una respuesta valida de negocio, no un fallo de infraestructura.
        throw new NoAvailableSpotException(
                "No hay plazas disponibles para el tipo de vehiculo " + vehicleType, e);
    } catch (RestClientException e) {
        // spot-service caido, timeout, 5xx... no es RN-01 (no confundir con "sin
        // plazas"): se traduce para que el frontend reciba un ErrorResponse
        // interpretable en vez de una excepcion de red cruda (IN-36).
        throw new SpotServiceException(
                "No se pudo contactar con spot-service para ocupar una plaza de tipo " + vehicleType, e);
    }

    if (response == null || response.id() == null) {
        // Un 200 sin id incumple el contrato de spot-service (RN-01 siempre
        // responde 409, nunca 200 vacio): es un fallo del servicio externo, no
        // un "sin plazas" legitimo.
        throw new SpotServiceException(
                "Respuesta invalida de spot-service al ocupar una plaza de tipo " + vehicleType);
    }

    return response.id();
}

    @Override
    public void releaseSpot(UUID spotId) {
        try {
            restClient.post()
                    .uri("/v1/spots/{id}/release", spotId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new SpotServiceException(
                    "No se pudo contactar con spot-service para liberar la plaza " + spotId, e);
        }
    }

    @Override
    public void updateSpotStatus(UUID spotId, String status) {
    try {
        restClient.patch()
                .uri("/v1/spots/{id}/status", spotId)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    } catch (RestClientException e) {
        throw new SpotServiceException(
                "No se pudo contactar con spot-service para actualizar el estado de la plaza " + spotId, e);
    }
}
}