package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayVehicleClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StayVehicleClientAdapterTest {

    private StayVehicleClientAdapter stayVehicleClientAdapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        stayVehicleClientAdapter = new StayVehicleClientAdapter(builder, "http://vehicle-service:8080");
    }

    @Test
    void shouldGetOrCreateVehicle() {
        // GIVEN
        UUID expectedVehicleId = UUID.randomUUID();
        String jsonResponse = """
                {
                    "id": "%s",
                    "plate": "1234ABC",
                    "type": "CAR",
                    "active": true
                }
                """.formatted(expectedVehicleId);

        server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        StayVehiclePort.VehicleInfo vehicleInfo = stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR);

        // THEN
        assertNotNull(vehicleInfo);
        assertEquals(expectedVehicleId, vehicleInfo.vehicleId());
        assertEquals("1234ABC", vehicleInfo.plate());
        assertEquals(VehicleType.CAR, vehicleInfo.vehicleType());
        assertTrue(vehicleInfo.active());
        server.verify();
    }
}