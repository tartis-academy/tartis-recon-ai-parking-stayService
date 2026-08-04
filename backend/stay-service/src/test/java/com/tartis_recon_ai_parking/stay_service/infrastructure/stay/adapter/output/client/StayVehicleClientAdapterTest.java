package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.dto.VehicleAttributes;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayVehicleClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
    StayVehiclePort.VehicleInfo vehicleInfo = stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY);

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
void shouldThrowVehicleServiceException_whenGetOrCreateVehicleGetFails() {
    // GIVEN: vehicle-service caido / devuelve 500 en la consulta (no 404, eso es negocio)
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

    // WHEN & THEN: no debe colarse la RestClientException cruda
    VehicleServiceException ex = assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));
    assertThat(ex.getCause()).isNotNull();
    server.verify();
}

@Test
void shouldThrowVehicleServiceException_whenGetOrCreateVehiclePostFails() {
    // GIVEN: matricula no existe (404, negocio) pero el alta posterior falla
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

    // WHEN & THEN
    VehicleServiceException ex = assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));
    assertThat(ex.getCause()).isNotNull();
    server.verify();
}

@Test
void shouldThrowInvalidStayException_whenPlateRejectedOnLookup() {
    // GIVEN: vehicle-service rechaza el formato de la matricula (400), no un 404
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(400));

    // WHEN & THEN: dato de entrada invalido, no "servicio no disponible"
    assertThrows(InvalidStayException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));
    server.verify();
}

@Test
void shouldThrowInvalidStayException_whenPlateRejectedOnCreate() {
    // GIVEN: matricula no existe (404, negocio) pero el alta la rechaza por formato (400)
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withRawStatus(400));

    // WHEN & THEN
    assertThrows(InvalidStayException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));
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
    StayVehiclePort.VehicleInfo vehicleInfo = stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY);

    // THEN
    assertNotNull(vehicleInfo);
    assertEquals(expectedVehicleId, vehicleInfo.vehicleId());
    server.verify();
}

@Test
void shouldSendOptionalAttributesOnCreation() {
    // GIVEN
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(UUID.randomUUID());

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.plate").value("1234ABC"))
            .andExpect(jsonPath("$.type").value("CAR"))
            .andExpect(jsonPath("$.brand").value("Seat"))
            .andExpect(jsonPath("$.model").value("Ibiza"))
            .andExpect(jsonPath("$.color").value("Rojo"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    stayVehicleClientAdapter.getOrCreateVehicle(
            "1234ABC", VehicleType.CAR, new VehicleAttributes("Seat", "Ibiza", "Rojo"));

    // THEN
    server.verify();
}

@Test
void shouldOmitAbsentOrBlankAttributesOnCreation() {
    // GIVEN
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(UUID.randomUUID());

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));

    // El totem manda "" en los campos que el operario no rellena; vehicle-service
    // los rechazaria con 400, asi que no deben viajar.
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.plate").value("1234ABC"))
            .andExpect(jsonPath("$.brand").doesNotExist())
            .andExpect(jsonPath("$.model").doesNotExist())
            .andExpect(jsonPath("$.color").doesNotExist())
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    stayVehicleClientAdapter.getOrCreateVehicle(
            "1234ABC", VehicleType.CAR, new VehicleAttributes("  ", null, ""));

    // THEN
    server.verify();
}

@Test
void shouldNotCreateVehicleWhenItAlreadyExists() {
    // GIVEN
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "type": "CAR",
                "active": true
            }
            """.formatted(UUID.randomUUID());

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    stayVehicleClientAdapter.getOrCreateVehicle(
            "1234ABC", VehicleType.CAR, new VehicleAttributes("Seat", "Ibiza", "Rojo"));

    // THEN: server.verify() falla si se ha lanzado el POST de alta.
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
void shouldThrowVehicleServiceException_whenFindByPlateUnreachable() {
    // GIVEN: vehicle-service caido / devuelve 500 (no 404, eso es negocio)
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

    // WHEN & THEN
    assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.findByPlate("1234ABC"));
    server.verify();
}

@Test
void shouldThrowInvalidStayException_whenFindByPlateRejected() {
    // GIVEN: vehicle-service rechaza el formato de la matricula (400), no un 404
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(400));

    // WHEN & THEN
    assertThrows(InvalidStayException.class,
            () -> stayVehicleClientAdapter.findByPlate("1234ABC"));
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
void shouldThrowVehicleServiceException_whenFindByIdUnreachable() {
    // GIVEN: vehicle-service caido / devuelve 500 (no 404, eso es negocio)
    UUID vehicleId = UUID.randomUUID();
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/" + vehicleId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

    // WHEN & THEN
    assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.findById(vehicleId));
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

@Test
void shouldThrowWhenResponseHasNoUniqueId() {
    // GIVEN: respuesta 200 pero sin uniqueId (respuesta invalida de vehicle-service)
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    // WHEN & THEN
    assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.findByPlate("1234ABC"));
    server.verify();
}

@Test
void shouldFallbackToRequestedTypeWhenResponseTypeMissing() {
    // GIVEN: vehicle-service no informa el tipo; debe usarse el que se pidio
    UUID vehicleId = UUID.randomUUID();
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "plate": "1234ABC",
                "active": true
            }
            """.formatted(vehicleId);

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    StayVehiclePort.VehicleInfo vehicleInfo =
            stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.MOTORBIKE, VehicleAttributes.EMPTY);

    // THEN
    assertEquals(VehicleType.MOTORBIKE, vehicleInfo.vehicleType());
    server.verify();
}

@Test
void shouldFallbackToRequestedPlateWhenResponsePlateMissing() {
    // GIVEN: vehicle-service no informa la matricula; debe usarse la que se pidio
    UUID vehicleId = UUID.randomUUID();
    String jsonResponse = """
            {
                "uniqueId": "%s",
                "type": "CAR",
                "active": true
            }
            """.formatted(vehicleId);

    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    // WHEN
    StayVehiclePort.VehicleInfo vehicleInfo =
            stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY);

    // THEN
    assertEquals("1234ABC", vehicleInfo.plate());
    server.verify();
}

// --- Separacion 401/403 vs resto de 4xx (translateClientError) ---
//
// Un rechazo de credenciales NO es una matricula invalida. Antes los dos
// caian en el mismo saco y un 401 llegaba al totem como "La matricula no es
// valida", lo que mandaba el diagnostico en la direccion contraria.

@Test
void shouldThrowVehicleServiceException_whenLookupUnauthorized() {
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(401));

    VehicleServiceException ex = assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));

    assertThat(ex.getMessage()).contains("credenciales");
    server.verify();
}

@Test
void shouldThrowVehicleServiceException_whenLookupForbidden() {
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(403));

    assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));
    server.verify();
}

@Test
void shouldThrowVehicleServiceException_whenCreateUnauthorized() {
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(404));
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withRawStatus(401));

    assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.getOrCreateVehicle("1234ABC", VehicleType.CAR, VehicleAttributes.EMPTY));
    server.verify();
}

@Test
void shouldStillThrowInvalidStay_whenPlateRejectedWith400() {
    // El 400 de formato sigue siendo un error del dato, no de credenciales.
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/MAL"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(400));

    InvalidStayException ex = assertThrows(InvalidStayException.class,
            () -> stayVehicleClientAdapter.findByPlate("MAL"));

    assertThat(ex.getMessage()).contains("no es valida");
    server.verify();
}

@Test
void shouldThrowVehicleServiceException_whenFindByPlateUnauthorized() {
    server.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/1234ABC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(401));

    assertThrows(VehicleServiceException.class,
            () -> stayVehicleClientAdapter.findByPlate("1234ABC"));
    server.verify();
}
}
