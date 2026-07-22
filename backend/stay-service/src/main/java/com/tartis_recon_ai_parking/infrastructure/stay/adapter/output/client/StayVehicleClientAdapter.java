package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
    Map<String, Object> response;

    try {
        // 1. Consulta por matrícula
        response = restClient.get()
                .uri("/v1/vehicles/plate/{plate}", plate)
                .retrieve()
                .body(Map.class);
    } catch (HttpClientErrorException.NotFound e) {
        // 2. Si no existe (404), lo crea vía POST
        response = restClient.post()
                .uri("/v1/vehicles")
                .body(Map.of(
                        "plate", plate,
                        "type", vehicleType.name()
                ))
                .retrieve()
                .body(Map.class);
    }

    // Comprobamos la clave REAL que devuelve el servicio ("uniqueId")
    if (response == null || !response.containsKey("uniqueId")) {
        throw new IllegalStateException("Respuesta inválida de vehicle-service: no se encontró 'uniqueId'");
    }

    // Mapeamos 'uniqueId' al campo 'vehicleId' de nuestro VehicleInfo local
    UUID vehicleId = UUID.fromString(response.get("uniqueId").toString());
    String responsePlate = (String) response.getOrDefault("plate", plate);
    
    String typeStr = (String) response.get("type");
    VehicleType type = typeStr != null ? VehicleType.valueOf(typeStr) : vehicleType;
    Boolean active = (Boolean) response.getOrDefault("active", true);

    return new VehicleInfo(vehicleId, responsePlate, type, active);
}
}