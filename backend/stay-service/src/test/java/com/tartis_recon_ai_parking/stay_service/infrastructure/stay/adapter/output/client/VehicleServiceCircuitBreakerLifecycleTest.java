package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.dto.VehicleAttributes;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayVehicleClientAdapter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RES-09: ciclo completo del circuit breaker de vehicleService, simulando la
 * caida del servicio con un stub HTTP.
 *
 * <p>Hermano de {@code SpotServiceCircuitBreakerLifecycleTest}, pero para el
 * adaptador de vehiculos, que hasta ahora no tenia ninguna prueba de sus tres
 * fallbacks.
 *
 * <p><b>Por que este harness es distinto.</b> El de spot guarda el
 * {@code MockRestServiceServer} en un unico campo estatico, asi que se queda
 * con el del ultimo {@code RestClient.Builder} que Spring procese. Eso le vale
 * porque {@code StaySpotClientAdapter} usa el builder generico, pero aqui no
 * sirve: {@code StayVehicleClientAdapter} inyecta
 * {@code @Qualifier("vehicleRestClientBuilder")}, un builder propio con el
 * timeout mas largo del proveedor externo (RES-06). Si se cogiera el servidor
 * equivocado, las expectativas no casarian y el test fallaria por motivos que
 * no tienen nada que ver con el circuito. Por eso aqui se indexan por nombre
 * de bean y se expone explicitamente el de vehiculo.
 *
 * <p>Configuracion de {@code vehicleService} (application.yml): ventana 20,
 * minimo 10 llamadas, umbral 50 %, y
 * {@code permitted-number-of-calls-in-half-open-state: 3} del bloque
 * {@code default}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(VehicleServiceCircuitBreakerLifecycleTest.VehicleMockServerConfig.class)
class VehicleServiceCircuitBreakerLifecycleTest {

    private static final String CIRCUIT = "vehicleService";
    private static final String PLATE = "1234ABC";
    private static final String LOOKUP_URL = "http://vehicle-service:8080/v1/vehicles/plate/" + PLATE;

    /** minimum-number-of-calls de vehicleService. */
    private static final int CALLS_TO_OPEN = 10;

    /** permitted-number-of-calls-in-half-open-state del bloque default. */
    private static final int CALLS_TO_CLOSE_FROM_HALF_OPEN = 3;

    private static final UUID VEHICLE_ID = UUID.randomUUID();

    /**
     * Indexa un {@code MockRestServiceServer} por cada {@code RestClient.Builder}
     * del contexto, en vez de quedarse solo con el ultimo.
     */
    @TestConfiguration
    static class VehicleMockServerConfig {

        static final String VEHICLE_BUILDER = "vehicleRestClientBuilder";

        private static final Map<String, MockRestServiceServer> SERVERS = new HashMap<>();

        @Bean
        public static BeanPostProcessor vehicleRestClientBuilderPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof RestClient.Builder builder) {
                        SERVERS.put(beanName, MockRestServiceServer.bindTo(builder).build());
                    }
                    return bean;
                }
            };
        }

        /** El servidor ligado al builder que usa StayVehicleClientAdapter. */
        @Bean
        public MockRestServiceServer vehicleMockRestServiceServer() {
            MockRestServiceServer server = SERVERS.get(VEHICLE_BUILDER);
            if (server == null) {
                throw new IllegalStateException(
                        "No se intercepto el builder '" + VEHICLE_BUILDER + "'. Builders vistos: "
                                + SERVERS.keySet()
                                + ". Si se renombro el @Bean en BeanConfiguration, actualizar VEHICLE_BUILDER.");
            }
            return server;
        }
    }

    @Autowired
    private StayVehicleClientAdapter adapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private MockRestServiceServer mockServer;

    @MockitoBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @BeforeEach
    void setUp() {
        OAuth2AuthorizedClient mockClient = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken mockToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        when(mockClient.getAccessToken()).thenReturn(mockToken);
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(mockClient);

        circuitBreakerRegistry.circuitBreaker(CIRCUIT).reset();
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        mockServer.reset();
    }

    // =========================================================================
    // CA-1 y CA-2: caida simulada y apertura del circuito
    // =========================================================================

    @Test
    @DisplayName("Con vehicle-service caido, el circuito se abre solo tras 10 fallos reales por HTTP")
    void circuitOpensAfterRealHttpFailures() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        givenVehicleServiceIsDown(ExpectedCount.times(CALLS_TO_OPEN));

        for (int i = 0; i < CALLS_TO_OPEN - 1; i++) {
            assertThatThrownBy(() -> adapter.findByPlate(PLATE))
                    .isInstanceOf(VehicleServiceException.class);
        }
        assertThat(circuitBreaker.getState())
                .as("con 9 fallos aun no se alcanza minimum-number-of-calls")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        assertThatThrownBy(() -> adapter.findByPlate(PLATE))
                .isInstanceOf(VehicleServiceException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("Un 404 es negocio (matricula no registrada) y NO abre el circuito")
    void notFoundDoesNotOpenTheCircuit() {
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(LOOKUP_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // 12 llamadas > minimum-number-of-calls: si el 404 contase como fallo,
        // el circuito abriria con creces.
        for (int i = 0; i < 12; i++) {
            assertThat(adapter.findByPlate(PLATE))
                    .as("404 en findByPlate se traduce a Optional vacio, no a excepcion")
                    .isEmpty();
        }

        assertThat(circuitBreakerRegistry.circuitBreaker(CIRCUIT).getState())
                .as("solo VehicleServiceException cuenta como fallo (record-exceptions)")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // =========================================================================
    // CA-3: ejecucion de los fallbacks
    // =========================================================================

    @Test
    @DisplayName("Con el circuito abierto, findByPlate ejecuta su fallback sin llamar al servicio")
    void findByPlateFallbackRunsWithoutReachingTheService() {
        circuitBreakerRegistry.circuitBreaker(CIRCUIT).transitionToOpenState();

        // Sin expectativas: si saliera una peticion, el mock server fallaria.
        assertThatThrownBy(() -> adapter.findByPlate(PLATE))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");

        mockServer.verify();
    }

    @Test
    @DisplayName("Con el circuito abierto, getOrCreateVehicle ejecuta su fallback")
    void getOrCreateVehicleFallbackRuns() {
        circuitBreakerRegistry.circuitBreaker(CIRCUIT).transitionToOpenState();

        assertThatThrownBy(
                () -> adapter.getOrCreateVehicle(PLATE, VehicleType.CAR, VehicleAttributes.EMPTY))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");

        mockServer.verify();
    }

    @Test
    @DisplayName("Con el circuito CERRADO y un 500, el fallback tambien se ejecuta (rama Throwable)")
    void fallbackAlsoRunsFromTheClosedPath() {
        // Los fallbacks de vehicle reciben Throwable, no CallNotPermittedException,
        // asi que Resilience4j tambien les enruta las excepciones que lanza el
        // cuerpo del metodo, no solo las de circuito abierto.
        //
        // Como distinguirlo, si el cuerpo y el fallback construyen el MISMO
        // mensaje: por la causa encadenada.
        //   - Si el fallback se ejecuta: envuelve la excepcion del cuerpo, asi
        //     que la causa es otra VehicleServiceException.
        //   - Si no se ejecutase y la del cuerpo saliera directa: la causa seria
        //     la RestClientException cruda del RestClient.
        //
        // Deja constancia ejecutable de que el fallback NO es codigo muerto en el
        // camino normal (revision de la PR #124).
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        givenVehicleServiceIsDown(ExpectedCount.once());

        assertThatThrownBy(() -> adapter.findByPlate(PLATE))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("No se pudo contactar")
                .as("la causa encadenada delata si paso por el fallback")
                .hasCauseInstanceOf(VehicleServiceException.class);

        assertThat(circuitBreaker.getState())
                .as("un solo fallo no alcanza minimum-number-of-calls")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        mockServer.verify();
    }

    @Test
    @DisplayName("Con el circuito abierto, findById ejecuta su fallback")
    void findByIdFallbackRuns() {
        circuitBreakerRegistry.circuitBreaker(CIRCUIT).transitionToOpenState();

        assertThatThrownBy(() -> adapter.findById(UUID.randomUUID()))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");

        mockServer.verify();
    }

    // =========================================================================
    // CA-4: recuperacion en semiabierto
    // =========================================================================

    @Test
    @DisplayName("Recuperacion completa: OPEN -> HALF_OPEN -> 3 llamadas correctas -> CLOSED")
    void recoversThroughHalfOpenWhenServiceComesBack() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);

        givenVehicleServiceIsDown(ExpectedCount.times(CALLS_TO_OPEN));
        for (int i = 0; i < CALLS_TO_OPEN; i++) {
            assertThatThrownBy(() -> adapter.findByPlate(PLATE))
                    .isInstanceOf(VehicleServiceException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // En produccion lo dispara automatic-transition-from-open-to-half-open
        // al cumplirse wait-duration-in-open-state (10s); aqui se fuerza.
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        mockServer.reset();
        givenVehicleServiceIsHealthy(ExpectedCount.times(CALLS_TO_CLOSE_FROM_HALF_OPEN));

        for (int i = 0; i < CALLS_TO_CLOSE_FROM_HALF_OPEN; i++) {
            assertThat(adapter.findByPlate(PLATE))
                    .as("en HALF_OPEN las llamadas de prueba si llegan al servicio")
                    .isPresent();
        }

        assertThat(circuitBreaker.getState())
                .as("3 llamadas correctas en HALF_OPEN cierran el circuito")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        mockServer.verify();
    }

    @Test
    @DisplayName("Si vehicle-service sigue caido en HALF_OPEN, el circuito vuelve a OPEN")
    void returnsToOpenWhenTheServiceIsStillDownInHalfOpen() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();

        givenVehicleServiceIsDown(ExpectedCount.times(CALLS_TO_CLOSE_FROM_HALF_OPEN));
        for (int i = 0; i < CALLS_TO_CLOSE_FROM_HALF_OPEN; i++) {
            assertThatThrownBy(() -> adapter.findByPlate(PLATE))
                    .isInstanceOf(VehicleServiceException.class);
        }

        assertThat(circuitBreaker.getState())
                .as("el servicio no se ha recuperado: el circuito debe reabrirse")
                .isEqualTo(CircuitBreaker.State.OPEN);

        mockServer.verify();
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /**
     * Stub de vehicle-service caido. Se usa 500 y no 404 a proposito: el 404 es
     * negocio (matricula no registrada) y el adaptador lo traduce a Optional
     * vacio sin contarlo como fallo.
     */
    private void givenVehicleServiceIsDown(ExpectedCount count) {
        mockServer.expect(count, requestTo(LOOKUP_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** Stub de vehicle-service sano: devuelve el vehiculo activo. */
    private void givenVehicleServiceIsHealthy(ExpectedCount count) {
        String body = "{\"uniqueId\":\"" + VEHICLE_ID + "\",\"plate\":\"" + PLATE
                + "\",\"type\":\"CAR\",\"active\":true}";
        mockServer.expect(count, requestTo(LOOKUP_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
