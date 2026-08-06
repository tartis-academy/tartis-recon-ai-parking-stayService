package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StaySpotClientAdapter;

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
 * RES-09: tests de resiliencia del ciclo completo del circuit breaker,
 * simulando la caida de spot-service con un stub HTTP.
 *
 * <p><b>Que aporta sobre lo que ya existia.</b> Los tests previos cubren piezas
 * sueltas, pero ninguna el ciclo entero:
 * <ul>
 *   <li>{@code CircuitBreakerBehaviourTest} comprueba que el circuito abre, pero
 *       llamando a {@code circuitBreaker.onError(...)} directamente sobre el
 *       registry, con una excepcion fabricada a mano. Nunca pasa por el
 *       adaptador ni por HTTP.</li>
 *   <li>{@code StaySpotClientAdapterFallbackTest} cubre el fallback, pero
 *       forzando el estado con {@code transitionToOpenState()}: da por hecho
 *       que el circuito ya esta abierto.</li>
 * </ul>
 *
 * <p><b>Por que importa que sea organico.</b> Que el circuito abra de verdad
 * depende de que la excepcion que lanza el adaptador este listada en
 * {@code record-exceptions} del {@code application.yml}. Al fabricar la
 * excepcion a mano, el test antiguo valida el circuit breaker de resilience4j
 * —que ya sabemos que funciona— pero no NUESTRA integracion con el. Si manana
 * alguien cambia el tipo de excepcion del adaptador o toca esa lista, el
 * circuito dejaria de abrirse en produccion y el test antiguo seguiria verde.
 * Aqui los fallos entran por el stub HTTP y suben por el adaptador, igual que
 * en produccion.
 *
 * <p>Configuracion de {@code spotService} que gobierna estos tests
 * (application.yml): ventana 20, minimo 10 llamadas, umbral 50 %, y
 * {@code permitted-number-of-calls-in-half-open-state: 3} heredado del bloque
 * {@code default}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SpotServiceCircuitBreakerLifecycleTest.MockServerConfig.class)
class SpotServiceCircuitBreakerLifecycleTest {

    private static final String CIRCUIT = "spotService";
    private static final String OCCUPY_URL = "http://spot-service:8080/v1/spots/occupy";

    /** Numero de fallos que abre el circuito: minimum-number-of-calls de spotService. */
    private static final int CALLS_TO_OPEN = 10;

    /** permitted-number-of-calls-in-half-open-state del bloque default. */
    private static final int CALLS_TO_CLOSE_FROM_HALF_OPEN = 3;

    /** Plaza que devuelve el stub cuando spot-service esta sano. */
    private static final UUID ASSIGNED_SPOT_ID = UUID.randomUUID();

    /**
     * Intercepta el {@code RestClient.Builder} ANTES de que el adaptador lo use
     * en su constructor.
     *
     * <p>Se indexa por nombre de bean en vez de guardar un unico
     * {@code MockRestServiceServer} estatico. Con un solo campo, cada builder
     * procesado pisa al anterior y acabas con el del ultimo que Spring cree:
     * hoy funcionaria de casualidad —{@code StaySpotClientAdapter} usa el
     * builder generico— pero en cuanto alguien anada otro builder no
     * cualificado, el mock quedaria apuntando al equivocado y el test fallaria
     * por un motivo que no tiene nada que ver con el circuito. Mismo patron que
     * {@code VehicleServiceCircuitBreakerLifecycleTest}.
     */
    @TestConfiguration
    static class MockServerConfig {

        static final String SPOT_BUILDER = "restClientBuilder";

        private static final Map<String, MockRestServiceServer> SERVERS = new HashMap<>();

        @Bean
        public static BeanPostProcessor lifecycleRestClientBuilderPostProcessor() {
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

        /** El servidor ligado al builder generico, que es el que usa el adaptador de plazas. */
        @Bean
        public MockRestServiceServer lifecycleMockRestServiceServer() {
            MockRestServiceServer server = SERVERS.get(SPOT_BUILDER);
            if (server == null) {
                throw new IllegalStateException(
                        "No se intercepto el builder '" + SPOT_BUILDER + "'. Builders vistos: "
                                + SERVERS.keySet()
                                + ". Si se renombro el @Bean en BeanConfiguration, actualizar SPOT_BUILDER.");
            }
            return server;
        }
    }

    @Autowired
    private StaySpotClientAdapter adapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private MockRestServiceServer mockServer;

    @MockitoBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @BeforeEach
    void setUp() {
        // Token OAuth2 falso: los tests no deben depender de Keycloak.
        OAuth2AuthorizedClient mockClient = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken mockToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        when(mockClient.getAccessToken()).thenReturn(mockToken);
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(mockClient);

        // Estado de partida explicito: el registry es un bean compartido entre
        // clases de test y otra puede haber dejado el circuito abierto.
        circuitBreakerRegistry.circuitBreaker(CIRCUIT).reset();
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        mockServer.reset();
    }

    // =========================================================================
    // CA-1 y CA-2: simular la caida del servicio y verificar que el circuito abre
    // =========================================================================

    @Test
    @DisplayName("Con spot-service caido, el circuito se abre solo tras 10 fallos reales por HTTP")
    void circuitOpensAfterRealHttpFailures() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        givenSpotServiceIsDown(ExpectedCount.times(CALLS_TO_OPEN));

        // Los 9 primeros no alcanzan minimum-number-of-calls: el circuito aun no
        // tiene evidencia suficiente y sigue cerrado.
        for (int i = 0; i < CALLS_TO_OPEN - 1; i++) {
            assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                    .isInstanceOf(SpotServiceException.class);
        }
        assertThat(circuitBreaker.getState())
                .as("con 9 fallos aun no se alcanza el minimo de llamadas")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // El decimo cruza el minimo con un 100 % de fallo: abre.
        assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                .isInstanceOf(SpotServiceException.class);
        assertThat(circuitBreaker.getState())
                .as("10 fallos al 100 % superan el umbral del 50 %")
                .isEqualTo(CircuitBreaker.State.OPEN);

        mockServer.verify();
    }

    // =========================================================================
    // CA-3: verificar la ejecucion del fallback
    // =========================================================================

    @Test
    @DisplayName("Con el circuito abierto se ejecuta el fallback y NO se llama a spot-service")
    void fallbackRunsAndNoRequestReachesTheService() {
        givenSpotServiceIsDown(ExpectedCount.times(CALLS_TO_OPEN));
        driveCallsUntilOpen();

        // Sin mas expectativas registradas: si el adaptador intentase llamar,
        // MockRestServiceServer fallaria por peticion inesperada. Que no falle
        // es la prueba de que el circuito corta la llamada de raiz.
        assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("circuito abierto");

        mockServer.verify();
    }

    // =========================================================================
    // CA-4: recuperacion en estado semiabierto
    // =========================================================================

    @Test
    @DisplayName("Recuperacion completa: OPEN -> HALF_OPEN -> 3 llamadas correctas -> CLOSED")
    void recoversThroughHalfOpenWhenServiceComesBack() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);

        givenSpotServiceIsDown(ExpectedCount.times(CALLS_TO_OPEN));
        driveCallsUntilOpen();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // En produccion esta transicion la dispara sola
        // automatic-transition-from-open-to-half-open-enabled al cumplirse
        // wait-duration-in-open-state (10s en spotService); aqui se fuerza para
        // no meter una espera real en el build.
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // spot-service se ha recuperado: responde correctamente.
        mockServer.reset();
        givenSpotServiceIsHealthy(ExpectedCount.times(CALLS_TO_CLOSE_FROM_HALF_OPEN));

        for (int i = 0; i < CALLS_TO_CLOSE_FROM_HALF_OPEN; i++) {
            assertThat(adapter.occupySpot(VehicleType.CAR))
                    .as("en HALF_OPEN las llamadas de prueba si llegan al servicio")
                    .isNotNull();
        }

        assertThat(circuitBreaker.getState())
                .as("3 llamadas correctas en HALF_OPEN cierran el circuito")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        mockServer.verify();
    }

    @Test
    @DisplayName("Si el servicio sigue caido en HALF_OPEN, el circuito vuelve a OPEN")
    void returnsToOpenWhenTheServiceIsStillDownInHalfOpen() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT);

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();

        // Basta con que fallen las llamadas de prueba permitidas: el umbral en
        // HALF_OPEN se evalua sobre esas 3, y un 100 % de fallo lo reabre.
        givenSpotServiceIsDown(ExpectedCount.times(CALLS_TO_CLOSE_FROM_HALF_OPEN));
        for (int i = 0; i < CALLS_TO_CLOSE_FROM_HALF_OPEN; i++) {
            assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                    .isInstanceOf(SpotServiceException.class);
        }

        assertThat(circuitBreaker.getState())
                .as("el servicio no se ha recuperado: el circuito debe reabrirse")
                .isEqualTo(CircuitBreaker.State.OPEN);

        mockServer.verify();
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /** Stub de spot-service caido: responde 500 a cada peticion de ocupacion. */
    private void givenSpotServiceIsDown(ExpectedCount count) {
        mockServer.expect(count, requestTo(OCCUPY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** Stub de spot-service sano: asigna una plaza. */
    private void givenSpotServiceIsHealthy(ExpectedCount count) {
        String body = "{\"id\":\"" + ASSIGNED_SPOT_ID + "\",\"status\":\"OCCUPIED\"}";
        mockServer.expect(count, requestTo(OCCUPY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void driveCallsUntilOpen() {
        for (int i = 0; i < CALLS_TO_OPEN; i++) {
            assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                    .isInstanceOf(SpotServiceException.class);
        }
    }
}
