package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StayTariffClientAdapter implements StayTariffPort {

    private final RestClient restClient;

    public StayTariffClientAdapter(
            RestClient.Builder builder,
            @Value("${services.tariff.url:http://tariff-service:8080}") String tariffServiceUrl
    ) {
        this.restClient = builder.baseUrl(tariffServiceUrl).build();
    }

    @Override
    public UUID getActiveTariffId(VehicleType vehicleType) {
        Map<?, ?> response = restClient.get()
                .uri("/v1/tariffs/active?type={type}", vehicleType.name())
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("id")) {
            throw new IllegalStateException("No se encontró tarifa activa para el tipo: " + vehicleType);
        }

        return UUID.fromString((String) response.get("id"));
    }

    @Override
    public BigDecimal calculateAmount(UUID tariffId, Instant checkIn, Instant checkOut) {
        Map<?, ?> response = restClient.post()
                .uri("/v1/tariffs/{id}/calculate", tariffId)
                .body(Map.of(
                        "checkIn", checkIn.toString(),
                        "checkOut", checkOut.toString()
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("amount")) {
            throw new IllegalStateException("Error al calcular el importe de la tarifa");
        }

        return new BigDecimal(response.get("amount").toString());
    }
}