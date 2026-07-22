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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StayTariffClientAdapterTest {

    private StayTariffClientAdapter stayTariffClientAdapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        stayTariffClientAdapter = new StayTariffClientAdapter(builder, "http://tariff-service:8080");
    }

    @Test
    void shouldGetActiveTariffId() {
        // GIVEN
        UUID expectedTariffId = UUID.randomUUID();
        String jsonResponse = "{\"id\": \"" + expectedTariffId + "\"}";

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs/active?type=CAR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        UUID actualTariffId = stayTariffClientAdapter.getActiveTariffId(VehicleType.CAR);

        // THEN
        assertEquals(expectedTariffId, actualTariffId);
        server.verify();
    }

    @Test
    void shouldCalculateAmount() {
        // GIVEN
        UUID tariffId = UUID.randomUUID();
        Instant checkIn = Instant.parse("2026-03-30T10:00:00Z");
        Instant checkOut = Instant.parse("2026-03-30T12:00:00Z");
        String jsonResponse = "{\"amount\": 15.50}";

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs/" + tariffId + "/calculate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        BigDecimal amount = stayTariffClientAdapter.calculateAmount(tariffId, checkIn, checkOut);

        assertEquals(0, new BigDecimal("15.50").compareTo(amount));
        server.verify();
    }
}