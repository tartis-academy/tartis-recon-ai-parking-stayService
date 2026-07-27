package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StaySpotClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
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
    void shouldOccupySpotSuccessfully() {
    // GIVEN
    UUID expectedSpotId = UUID.randomUUID();

    // CORRECCIÓN: Usar "id" en lugar de "spotId" en el JSON simulado
    String jsonResponse = """
            {
                "id": "%s",
                "status": "OCCUPIED"
            }
            """.formatted(expectedSpotId);

    server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.vehicleType").value("CAR"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    UUID spotId = staySpotClientAdapter.occupySpot(VehicleType.CAR);

    // THEN
    assertNotNull(spotId);
    assertEquals(expectedSpotId, spotId);
    server.verify();
}

    @Test
    void shouldThrowWhenNoSpotsAvailable() {
        // GIVEN: spot-service responde sin id (parking completo / respuesta invalida)
        server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // WHEN & THEN
        assertThrows(IllegalStateException.class,
                () -> staySpotClientAdapter.occupySpot(VehicleType.CAR));
        server.verify();
    }

    @Test
    void shouldReleaseSpot() {
        // GIVEN
        UUID spotId = UUID.randomUUID();

        server.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        // WHEN
        staySpotClientAdapter.releaseSpot(spotId);

        // THEN
        server.verify();
    }

    @Test
    void shouldSendStatusKeyWhenUpdatingSpotStatus() {
        // GIVEN
        // PATCH /v1/spots/{id}/status espera {"status": ...}. Se comprueba la clave
        // porque el contrato lo verifica el body, no la URL.
        UUID spotId = UUID.randomUUID();

        server.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andRespond(withSuccess());

        // WHEN
        staySpotClientAdapter.updateSpotStatus(spotId, "UNAVAILABLE");

        // THEN
        server.verify();
    }
}
