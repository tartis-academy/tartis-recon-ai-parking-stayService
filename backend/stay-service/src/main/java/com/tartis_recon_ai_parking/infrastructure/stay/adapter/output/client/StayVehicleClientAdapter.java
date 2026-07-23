package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.VehicleResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class StayVehicleClientAdapter implements StayVehiclePort {

    private final RestClient restClient;

    public StayVehicleClientAdapter(
            RestClient.Builder builder,
            @Value("${services.vehicle.url:http://vehicle-service:8080}") String vehicleServiceUrl
    ) {
        this.restClient = builder.baseUrl(vehicleServiceUrl).build();
    }


    @Override
public VehicleInfo getOrCreateVehicle(String plate, VehicleType vehicleType) {
    VehicleResponse response;

    try {
        // 1. Consulta por matrícula
        response = restClient.get()
                .uri("/v1/vehicles/plate/{plate}", plate)
                .retrieve()
                .body(VehicleResponse.class); // <-- DTO en lugar de Map.class
    } catch (HttpClientErrorException.NotFound e) {
        // 2. Si no existe (404), lo crea vía POST
        response = restClient.post()
                .uri("/v1/vehicles")
                .body(Map.of(
                        "plate", plate,
                        "type", vehicleType.name()
                ))
                .retrieve()
                .body(VehicleResponse.class); // <-- DTO en lugar de Map.class
    }

    if (response == null || response.uniqueId() == null) {
        throw new IllegalStateException("Respuesta inválida de vehicle-service");
    }

    VehicleType type = response.type() != null ? VehicleType.valueOf(response.type()) : vehicleType;

    return new VehicleInfo(
            response.uniqueId(),
            response.plate() != null ? response.plate() : plate,
            type,
            response.isActive()
    );
}
}