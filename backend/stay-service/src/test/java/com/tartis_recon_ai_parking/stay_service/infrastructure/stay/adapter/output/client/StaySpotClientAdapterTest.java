package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StaySpotClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
    void shouldThrowSpotServiceException_whenResponseHasNoId() {
        // GIVEN: spot-service responde 200 pero sin id. Segun su contrato real
        // (openapi.yml + OccupySpotUseCase), RN-01 siempre es 409, nunca un 200
        // vacio: esto es un incumplimiento de contrato, no "sin plazas".
        server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // WHEN & THEN
        assertThrows(SpotServiceException.class,
                () -> staySpotClientAdapter.occupySpot(VehicleType.CAR));
        server.verify();
    }

    @Test
    void shouldThrowNoAvailableSpot_whenSpotServiceReturns409() {
        // GIVEN: contrato confirmado (openapi.yml de spot-service): RN-01
        // "sin plazas" se modela siempre como 409, nunca como 200 con id null.
        server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withRawStatus(409));

        // WHEN & THEN: debe ser RN-01 (sin plazas), no SpotServiceException (503)
        assertThrows(NoAvailableSpotException.class,
                () -> staySpotClientAdapter.occupySpot(VehicleType.CAR));
        server.verify();
    }

    @Test
    void shouldThrowSpotServiceException_whenOccupySpotUnreachable() {
        // GIVEN: spot-service caido / devuelve 500 (fallo de infraestructura, no de negocio)
        server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // WHEN & THEN: no debe colarse la RestClientException cruda
        SpotServiceException ex = assertThrows(SpotServiceException.class,
                () -> staySpotClientAdapter.occupySpot(VehicleType.CAR));
        assertThat(ex.getCause()).isNotNull();
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

    @Test
    void shouldThrowSpotServiceException_whenUpdateStatusFails() {
        // GIVEN
        UUID spotId = UUID.randomUUID();
        server.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError());

        // WHEN & THEN
        assertThrows(SpotServiceException.class,
                () -> staySpotClientAdapter.updateSpotStatus(spotId, "UNAVAILABLE"));
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
    void shouldThrowSpotServiceException_whenReleaseSpotFails() {
        // GIVEN
        UUID spotId = UUID.randomUUID();
        server.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // WHEN & THEN
        assertThrows(SpotServiceException.class,
                () -> staySpotClientAdapter.releaseSpot(spotId));
        server.verify();
    }
}
