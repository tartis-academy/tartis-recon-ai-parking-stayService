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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
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
    
    // JSON simula la respuesta real del microservicio externo con "uniqueId"
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(expectedVehicleId);

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    StayVehiclePort.VehicleInfo vehicleInfo = stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR);

    // THEN
    assertNotNull(vehicleInfo);
    // Verificamos que se mapeó correctamente a nuestro record interno
    assertEquals(expectedVehicleId, vehicleInfo.vehicleId());
    assertEquals("1234ABC", vehicleInfo.plate());
    assertEquals(VehicleType.CAR, vehicleInfo.vehicleType());
    assertTrue(vehicleInfo.active());
    
    server.verify();
}

@Test
void shouldCreateVehicleWhenNotFoundByPlate() {
    // GIVEN
    UUID expectedVehicleId = UUID.randomUUID();
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(expectedVehicleId);

    // 1. Espera el primer intento (GET) y devuelve 404
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));

    // 2. Espera el segundo intento (POST) para crearlo y devuelve éxito
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    StayVehiclePort.VehicleInfo vehicleInfo = stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR);

    // THEN
    assertNotNull(vehicleInfo);
    assertEquals(expectedVehicleId, vehicleInfo.vehicleId());
    server.verify();
}

@Test
void shouldFindVehicleByPlate() {
    // GIVEN
    UUID expectedVehicleId = UUID.randomUUID();
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(expectedVehicleId);

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    Optional<StayVehiclePort.VehicleInfo> vehicleInfo = stayVehicleClientAdapter.findByPlate("1234ABC");

    // THEN
    assertTrue(vehicleInfo.isPresent());
    assertEquals(expectedVehicleId, vehicleInfo.get().vehicleId());
    server.verify();
}

@Test
void shouldReturnEmptyWhenPlateNotFound() {
    // GIVEN: vehicle-service no conoce la matricula (no debe crearla)
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));

    // WHEN
    Optional<StayVehiclePort.VehicleInfo> vehicleInfo = stayVehicleClientAdapter.findByPlate("1234ABC");

    // THEN
    assertTrue(vehicleInfo.isEmpty());
    server.verify();
}

@Test
void shouldFindVehicleById() {
    // GIVEN
    UUID vehicleId = UUID.randomUUID();
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(vehicleId);

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/" + vehicleId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    Optional<StayVehiclePort.VehicleInfo> vehicleInfo = stayVehicleClientAdapter.findById(vehicleId);

    // THEN
    assertTrue(vehicleInfo.isPresent());
    assertEquals(vehicleId, vehicleInfo.get().vehicleId());
    assertEquals("1234ABC", vehicleInfo.get().plate());
    server.verify();
}

@Test
void shouldReturnEmptyWhenVehicleIdNotFound() {
    // GIVEN
    UUID vehicleId = UUID.randomUUID();
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/" + vehicleId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));

    // WHEN
    Optional<StayVehiclePort.VehicleInfo> vehicleInfo = stayVehicleClientAdapter.findById(vehicleId);

    // THEN
    assertTrue(vehicleInfo.isEmpty());
    server.verify();
}
}