package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.application.stay.dto.VehicleAttributes;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayVehicleClientAdapter;

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
 * STAY-105: los fallbacks de StayVehicleClientAdapter no deben enmascarar las
 * respuestas de negocio de vehicle-service. Va por el proxy real de Resilience4j
 * a proposito: llamando al adaptador directamente el fallback nunca se ejecuta y
 * el bug no se ve.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StayVehicleClientAdapterFallbackTest.MockServerConfig.class)
class StayVehicleClientAdapterFallbackTest {

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
                    // Solo el builder de vehicle: hay varios (RES-06 le da su propio
                    // read timeout) y engancharse a cualquiera deja al adaptador
                    // hablando con un mock server distinto del que se instrumenta.
                    if (bean instanceof RestClient.Builder builder
                            && "vehicleRestClientBuilder".equals(beanName)) {
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
    private StayVehicleClientAdapter adapter;

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
    void getOrCreateVehicleFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("vehicleService").transitionToOpenState();

        assertThatThrownBy(() -> adapter.getOrCreateVehicle("1234BCD", VehicleType.CAR, null))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");
    }

    @Test
    void getOrCreateVehicle_propagatesInvalidStayException_whenPlateRejectedOnCreate() {
        circuitBreakerRegistry.circuitBreaker("vehicleService").transitionToClosedState();

        mockServer.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/7777SSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // El fallback no debe convertir este 400 en un 503 "no se pudo contactar"
        assertThatThrownBy(() -> adapter.getOrCreateVehicle("7777SSE", VehicleType.CAR, null))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("no es valida");

        mockServer.verify();
    }

    @Test
    void findByPlate_propagatesInvalidStayException_whenPlateRejected() {
        circuitBreakerRegistry.circuitBreaker("vehicleService").transitionToClosedState();

        mockServer.expect(requestTo("http://vehicle-service:8080/v1/vehicles/plate/NO-VALIDA"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.findByPlate("NO-VALIDA"))
                .isInstanceOf(InvalidStayException.class);

        mockServer.verify();
    }
}
