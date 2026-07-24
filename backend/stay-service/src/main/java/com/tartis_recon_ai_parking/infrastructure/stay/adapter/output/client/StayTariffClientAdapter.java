package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.CalculateAmountRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.CalculateAmountResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.TariffResponse;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class StayTariffClientAdapter implements StayTariffPort {

    private final RestClient restClient;

    public StayTariffClientAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://tariff-service:8080").build();
    }

    @Override
    public UUID getActiveTariffId(VehicleType vehicleType) {
        List<TariffResponse> tariffs = restClient.get()
                .uri("/v1/tariffs/active?type={type}", vehicleType.name())
                .retrieve()
                .body(new ParameterizedTypeReference<List<TariffResponse>>() {});

        if (tariffs == null || tariffs.isEmpty()) {
            throw new IllegalStateException("No hay tarifa activa configurada para el tipo de vehículo: " + vehicleType);
        }

        return tariffs.get(0).id();
    }

    @Override
    public BigDecimal calculateAmount(VehicleType vehicleType, long totalMinutes) {
        CalculateAmountResponse response = restClient.post()
                .uri("/v1/tariffs/calculate")
                .body(new CalculateAmountRequest(vehicleType.name(), totalMinutes))
                .retrieve()
                .body(CalculateAmountResponse.class);

        if (response == null || response.amount() == null) {
            throw new IllegalStateException(
                    "tariff-service no devolvió importe para el tipo de vehículo: " + vehicleType);
        }

        return response.amount();
    }
}
