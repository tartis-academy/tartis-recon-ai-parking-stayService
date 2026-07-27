package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayTariffClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StayTariffClientAdapterTest {

    private StayTariffClientAdapter stayTariffClientAdapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        // 1. Crear un RestClient.Builder manual sin levantar contexto de Spring
        RestClient.Builder builder = RestClient.builder();

        // 2. Vincular el MockRestServiceServer al builder para interceptar peticiones HTTP
        server = MockRestServiceServer.bindTo(builder).build();

        // 3. Instanciar el adaptador inyectándole el builder mockeado
        stayTariffClientAdapter = new StayTariffClientAdapter(builder);
    }

    @Test
    void shouldGetActiveTariffIdSuccessfully() {
        // GIVEN
        UUID expectedTariffId = UUID.randomUUID();

        // El endpoint /v1/tariffs/active devuelve una lista JSON [...]
        String jsonResponse = """
                [
                    {
                        "id": "%s",
                        "vehicleType": "CAR",
                        "active": true
                    }
                ]
                """.formatted(expectedTariffId);

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs/active?type=CAR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        UUID resultTariffId = stayTariffClientAdapter.getActiveTariffId(VehicleType.CAR);

        // THEN
        assertNotNull(resultTariffId);
        assertEquals(expectedTariffId, resultTariffId);
        server.verify();
    }

    @Test
    void shouldThrowExceptionWhenNoActiveTariffFound() {
        // GIVEN
        String emptyJsonResponse = "[]";

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs/active?type=MOTORBIKE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(emptyJsonResponse, MediaType.APPLICATION_JSON));

        // WHEN & THEN
        assertThrows(IllegalStateException.class, () ->
            stayTariffClientAdapter.getActiveTariffId(VehicleType.MOTORBIKE)
        );

        server.verify();
    }

    @Test
    void shouldCalculateAmountSuccessfully() {
        // GIVEN: stay envia { type, minutes } y tariff (PriceResponse) devuelve solo { price }
        String jsonResponse = """
                {
                    "price": 2.80
                }
                """;

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs/calculate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"type\":\"CAR\",\"minutes\":90}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        BigDecimal amount = stayTariffClientAdapter.calculateAmount(VehicleType.CAR, 90L);

        // THEN
        assertEquals(0, amount.compareTo(new BigDecimal("2.80")));
        server.verify();
    }

    @Test
    void shouldThrowExceptionWhenTariffReturnsNoAmount() {
        // GIVEN: respuesta sin price
        String jsonResponse = "{}";

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs/calculate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN & THEN
        assertThrows(IllegalStateException.class, () ->
            stayTariffClientAdapter.calculateAmount(VehicleType.CAR, 90L)
        );

        server.verify();
    }
}