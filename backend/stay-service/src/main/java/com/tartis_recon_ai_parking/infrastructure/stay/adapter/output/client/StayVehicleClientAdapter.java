package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

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
        Map<?, ?> response = restClient.post()
                .uri("/v1/vehicles/resolve")
                .body(Map.of(
                        "plate", plate,
                        "vehicleType", vehicleType != null ? vehicleType.name() : ""
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("id")) {
            throw new IllegalStateException("No se pudo obtener o registrar el vehículo");
        }

        return new VehicleInfo(
                UUID.fromString((String) response.get("id")),
                (String) response.get("plate"),
                VehicleType.valueOf((String) response.get("type")),
                (Boolean) response.get("active")
        );
    }
}