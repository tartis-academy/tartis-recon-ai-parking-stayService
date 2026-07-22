package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StaySpotClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StaySpotClientAdapterTest {

    private StaySpotClientAdapter staySpotClientAdapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        // 1. Creamos el builder real de RestClient
        RestClient.Builder builder = RestClient.builder();

        // 2. Vinculamos el MockRestServiceServer al builder
        server = MockRestServiceServer.bindTo(builder).build();

        // 3. Instanciamos el adaptador con la URL mockeada
        staySpotClientAdapter = new StaySpotClientAdapter(builder, "http://spot-service:8080");
    }

    @Test
    void shouldAssignSpotSuccessfully() {
        // GIVEN
        UUID expectedSpotId = UUID.randomUUID();
        String jsonResponse = "{\"spotId\": \"" + expectedSpotId + "\"}";

        server.expect(requestTo("http://spot-service:8080/v1/spots/assign"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        UUID actualSpotId = staySpotClientAdapter.assignSpot(VehicleType.CAR);

        // THEN
        assertEquals(expectedSpotId, actualSpotId);
        server.verify(); // Confirma que la petición HTTP ocurrió
    }
}