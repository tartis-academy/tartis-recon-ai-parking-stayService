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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * RES-04: cubre especificamente occupySpotFallback() de StaySpotClientAdapter.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StaySpotClientAdapterFallbackTest.MockServerConfig.class)
class StaySpotClientAdapterFallbackTest {

    /**
     * 🛠️ Intercepta RestClient.Builder ANTES de que StaySpotClientAdapter lo use en su constructor.
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
                Instant.now().plusSeconds(3600)
        );

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
}