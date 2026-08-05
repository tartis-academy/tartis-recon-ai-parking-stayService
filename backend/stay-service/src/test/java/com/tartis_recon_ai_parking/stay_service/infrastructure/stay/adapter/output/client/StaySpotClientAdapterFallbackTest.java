package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StaySpotClientAdapter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
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
 * RES-04: cubre especificamente occupySpotFallback() de StaySpotClientAdapter.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StaySpotClientAdapterFallbackTest.MockServerConfig.class)
class StaySpotClientAdapterFallbackTest {

    /**
     * 🛠️ Intercepta RestClient.Builder ANTES de que StaySpotClientAdapter lo use
     * en su constructor.
     */
    @TestConfiguration
    static class MockServerConfig {
        private static MockRestServiceServer mockServer;

        @Bean
        public static BeanPostProcessor restClientBuilderPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof RestClient.Builder builder) {
                        mockServer = MockRestServiceServer.bindTo(builder).build();
                    }
                    return bean;
                }
            };
        }

        @Bean
        public MockRestServiceServer mockRestServiceServer() {
            return mockServer;
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
        // Mock del token OAuth2 para evitar conectar con Keycloak
        OAuth2AuthorizedClient mockClient = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken mockToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));

        when(mockClient.getAccessToken()).thenReturn(mockToken);
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(mockClient);
    }

    @AfterEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        mockServer.reset();
    }

    @Test
    void occupySpotFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToOpenState();

        assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("circuito abierto");
    }

    @Test
    void occupySpot_propagatesNoAvailableSpotException_whenServerReturns409() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToClosedState();

        mockServer.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                .isInstanceOf(NoAvailableSpotException.class)
                .hasMessageContaining("No hay plazas disponibles");

        mockServer.verify();
    }

    @Test
    void occupySpot_repeated409s_doNotOpenTheCircuit() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToClosedState();

        mockServer.expect(ExpectedCount.manyTimes(), requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        // 12 llamadas > minimum-number-of-calls (10) de spotService: si el
        // 409 contase como fallo, esto abriria el circuito con creces.
        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                    .isInstanceOf(NoAvailableSpotException.class);
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("spotService").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        mockServer.verify();
    }

    @Test
    void releaseSpotFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToOpenState();

        UUID spotId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.releaseSpot(spotId))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("circuito abierto");
    }

    @Test
    void releaseSpot_succeedsNormally_whenCircuitIsClosed() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToClosedState();

        UUID spotId = UUID.randomUUID();

        mockServer.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        adapter.releaseSpot(spotId);

        mockServer.verify();
    }

    @Test
    void updateSpotStatusFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToOpenState();

        UUID spotId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.updateSpotStatus(spotId, "RESERVED"))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("circuito abierto");
    }

    @Test
    void updateSpotStatus_succeedsNormally_whenCircuitIsClosed() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToClosedState();

        UUID spotId = UUID.randomUUID();

        mockServer.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());

        adapter.updateSpotStatus(spotId, "RESERVED");

        mockServer.verify();
    }

    // =========================================================================
    // PROPAGACIÓN DE ERRORES HTTP EN CIRCUITO CERRADO
    // =========================================================================

    @Test
    void releaseSpot_propagatesSpotServiceException_whenServerReturnsError() {
        // GIVEN: El circuito está CERRADO y spot-service devuelve un error HTTP (ej.
        // 404 Not Found)
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToClosedState();

        UUID spotId = UUID.randomUUID();

        mockServer.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // WHEN & THEN: Comprobamos que el proxy no enmascara la excepción y llega la
        // SpotServiceException original
        assertThatThrownBy(() -> adapter.releaseSpot(spotId))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("No se pudo contactar con spot-service para liberar la plaza");

        mockServer.verify();
    }

    @Test
    void updateSpotStatus_propagatesSpotServiceException_whenServerReturnsError() {
        // GIVEN: El circuito está CERRADO y spot-service devuelve un error HTTP (ej.
        // 400 Bad Request)
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToClosedState();

        UUID spotId = UUID.randomUUID();

        mockServer.expect(requestTo("http://spot-service:8080/v1/spots/" + spotId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // WHEN & THEN: Comprobamos que el proxy no enmascara la excepción y llega la
        // SpotServiceException original
        assertThatThrownBy(() -> adapter.updateSpotStatus(spotId, "INVALID_STATUS"))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("No se pudo contactar con spot-service para actualizar el estado");

        mockServer.verify();
    }
}