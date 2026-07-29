package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.NoActiveTariffException;
import com.tartis_recon_ai_parking.domain.stay.exception.TariffServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.CalculateAmountRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.CalculateAmountResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.TariffResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class StayTariffClientAdapter implements StayTariffPort {

    private final RestClient restClient;

    public StayTariffClientAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${services.tariff.url:http://tariff-service:8080}") String tariffServiceUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(tariffServiceUrl).build();
    }

    @Override
    public UUID getActiveTariffId(VehicleType vehicleType) {
        List<TariffResponse> tariffs;
        try {
            tariffs = restClient.get()
                    .uri("/v1/tariffs/active?type={type}", vehicleType.name())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TariffResponse>>() {});
        } catch (RestClientException e) {
            // tariff-service caido, timeout, 5xx... no es IN-08 (no confundir con
            // "sin tarifa activa"): se traduce para que el frontend reciba un
            // ErrorResponse interpretable en vez de una excepcion de red cruda (IN-36).
            throw new TariffServiceException(
                    "No se pudo contactar con tariff-service para obtener la tarifa activa de "
                            + vehicleType, e);
        }

        if (tariffs == null || tariffs.isEmpty()) {
            // IN-08: respuesta valida de negocio, no un fallo de infraestructura.
            throw new NoActiveTariffException(
                    "No hay tarifa activa configurada para el tipo de vehículo: " + vehicleType);
        }

        return tariffs.get(0).id();
    }

    @Override
    public BigDecimal calculateAmount(VehicleType vehicleType, long totalMinutes) {
        CalculateAmountResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/tariffs/calculate")
                    .body(new CalculateAmountRequest(vehicleType.name(), totalMinutes))
                    .retrieve()
                    .body(CalculateAmountResponse.class);
        } catch (RestClientException e) {
            throw new TariffServiceException(
                    "No se pudo contactar con tariff-service para calcular el importe de "
                            + vehicleType, e);
        }

        if (response == null || response.price() == null) {
            // Respuesta 200 pero incompleta: contrato incumplido por tariff-service,
            // no un fallo de red, pero tampoco algo que el cliente pueda corregir.
            throw new TariffServiceException(
                    "tariff-service no devolvió importe para el tipo de vehículo: " + vehicleType);
        }

        return response.price();
    }
}
