package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.rest.dto.VehicleResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class StayVehicleClientAdapter implements StayVehiclePort {

    private final RestClient restClient;
    private static final String CIRCUIT_BREAKER_NAME = "vehicleService";

    public StayVehicleClientAdapter(
            // RES-06: builder con el read timeout propio de vehicle (proveedor
            // externo mas lento), no el generico. Ver BeanConfiguration.
            @Qualifier("vehicleRestClientBuilder") RestClient.Builder builder,
            @Value("${services.vehicle.url:http://vehicle-service:8080}") String vehicleServiceUrl
    ) {
        this.restClient = builder.baseUrl(vehicleServiceUrl).build();
    }

    @Override
 @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getOrCreateVehicleFallback")
    public VehicleInfo getOrCreateVehicle(String plate, VehicleType vehicleType) {
        VehicleResponse response;

        try {
            // 1. Consulta por matrícula
            response = restClient.get()
                    .uri("/v1/vehicles/plate/{plate}", plate)
                    .retrieve()
                    .body(VehicleResponse.class); // <-- DTO en lugar de Map.class
        } catch (HttpClientErrorException.NotFound e) {
            // 2. Si no existe (404), lo crea vía POST. 404 es una respuesta de
            // negocio legitima (matricula aun no registrada), no un fallo.
            try {
                response = restClient.post()
                        .uri("/v1/vehicles")
                        .body(Map.of(
                                "plate", plate,
                                "type", vehicleType.name()))
                        .retrieve()
                        .body(VehicleResponse.class); // <-- DTO en lugar de Map.class
            } catch (HttpClientErrorException creationRejected) {
                // vehicle-service valida el formato de la matricula (p.ej. longitud,
                // caracteres) y rechaza el alta con 4xx: es un dato de entrada
                // invalido del propio check-in, no un fallo de vehicle-service. Sin
                // esto, una matricula mal escrita en el totem se traducia como "503
                // servicio no disponible" en vez de "400 matricula invalida".
                throw translateClientError(creationRejected, plate);
            } catch (RestClientException creationFailure) {
                throw new VehicleServiceException(
                        "No se pudo contactar con vehicle-service para dar de alta el vehiculo " + plate,
                        creationFailure);
            }
        } catch (HttpClientErrorException e) {
            // Mismo caso que arriba pero en la propia consulta (algunos vehicle-service
            // validan el formato tambien en el GET, antes de responder 404/200).
            throw translateClientError(e, plate);
        } catch (RestClientException e) {
            // vehicle-service caido, timeout, 5xx... no confundir con el 404/4xx de
            // arriba (esos son negocio); se traduce para que el frontend reciba un
            // ErrorResponse interpretable en vez de una excepcion de red cruda (IN-36).
            throw new VehicleServiceException(
                    "No se pudo contactar con vehicle-service para consultar el vehiculo " + plate, e);
        }

        return toVehicleInfo(response, plate, vehicleType);
    }

    private VehicleInfo getOrCreateVehicleFallback(String plate, VehicleType vehicleType, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            throw new VehicleServiceException(
                    "vehicle-service no responde con normalidad ahora mismo (circuito abierto); "
                            + "no se pudo verificar/crear el vehiculo " + plate,
                    t);
        }
        throw new VehicleServiceException(
                "No se pudo contactar con vehicle-service para verificar/crear el vehiculo " + plate, t);
    }

    @Override
 @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "findByPlateFallback")
    public Optional<VehicleInfo> findByPlate(String plate) {
        try {
            VehicleResponse response = restClient.get()
                    .uri("/v1/vehicles/plate/{plate}", plate)
                    .retrieve()
                    .body(VehicleResponse.class);

            return Optional.of(toVehicleInfo(response, plate, null));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            // Matricula con formato invalido: dato de entrada erroneo, no un
            // fallo de vehicle-service (mismo caso que en getOrCreateVehicle).
            throw translateClientError(e, plate);
        } catch (RestClientException e) {
            throw new VehicleServiceException(
                    "No se pudo contactar con vehicle-service para consultar el vehiculo " + plate, e);
        }
    }

     private Optional<VehicleInfo> findByPlateFallback(String plate, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            throw new VehicleServiceException(
                    "vehicle-service no responde con normalidad ahora mismo (circuito abierto); "
                            + "no se pudo consultar el vehiculo " + plate, t);
        }
        throw new VehicleServiceException(
                "No se pudo contactar con vehicle-service para consultar el vehiculo " + plate, t);
    }


    /**
     * Un 401/403 de vehicle-service no es una matricula mal escrita: es que
     * stay-service no se ha autenticado bien contra el (token de servicio
     * ausente, caducado, o sin el rol que exige el endpoint).
     *
     * <p>
     * Antes caia en el mismo saco que el 400 de formato, asi que un fallo de
     * autenticacion llegaba al totem como <em>"La matricula '1234BCD' no es
     * valida"</em>. En la prueba E2E del 30/07 eso mando el diagnostico en la
     * direccion contraria durante un buen rato: se buscaba un problema de
     * validacion de matriculas cuando lo que pasaba era que stay no mandaba
     * ninguna cabecera Authorization.
     *
     * <p>
     * Se separa para que salga como 503 (igual que el resto de fallos de
     * integracion, IN-36) y con un mensaje que apunta a la causa real.
     */
    private static RuntimeException translateClientError(HttpClientErrorException e, String plate) {
        int status = e.getStatusCode().value();

        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new VehicleServiceException(
                    "vehicle-service rechazo las credenciales de stay-service (" + e.getStatusCode()
                            + "). No es un problema de la matricula '" + plate
                            + "': revisar el token de servicio (STAY_CLIENT_ID/STAY_CLIENT_SECRET)"
                            + " y los roles de su cuenta de servicio.",
                    e);
        }

        return new InvalidStayException(
                "La matricula '" + plate + "' no es valida: vehicle-service la rechazo ("
                        + e.getStatusCode() + ")",
                e);
    }

    /**
     * {@code GET /v1/vehicles/{id}} (VehicleRestAdapter.getVehicleById en
     * vehicle-service), confirmado contra su codigo: usa el mismo
     * {@code mapper.toResponse(vehicle)} que {@code /v1/vehicles/plate/{plate}},
     * asi que devuelve la misma forma de {@link VehicleResponse}.
     */
    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "findByIdFallback")
    public Optional<VehicleInfo> findById(UUID vehicleId) {
        try {
            VehicleResponse response = restClient.get()
                    .uri("/v1/vehicles/{id}", vehicleId)
                    .retrieve()
                    .body(VehicleResponse.class);

            return Optional.of(toVehicleInfo(response, null, null));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new VehicleServiceException(
                    "No se pudo contactar con vehicle-service para consultar el vehiculo " + vehicleId, e);
        }
    }

     private Optional<VehicleInfo> findByIdFallback(UUID vehicleId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            throw new VehicleServiceException(
                    "vehicle-service no responde con normalidad ahora mismo (circuito abierto); "
                            + "no se pudo consultar el vehiculo " + vehicleId, t);
        }
        throw new VehicleServiceException(
                "No se pudo contactar con vehicle-service para consultar el vehiculo " + vehicleId, t);
    }

    private static VehicleInfo toVehicleInfo(VehicleResponse response, String plate, VehicleType fallbackType) {
        if (response == null || response.uniqueId() == null) {
            // Respuesta valida en forma pero incompleta: contrato incumplido por
            // vehicle-service, no un fallo de red (IN-36).
            throw new VehicleServiceException("Respuesta inválida de vehicle-service");
        }

        VehicleType type = response.type() != null ? VehicleType.valueOf(response.type()) : fallbackType;

        return new VehicleInfo(
                response.uniqueId(),
                response.plate() != null ? response.plate() : plate,
                type,
                response.isActive());
    }
}