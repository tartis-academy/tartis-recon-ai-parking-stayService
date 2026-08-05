package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort.EntryTicketInfo;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayTicketClientAdapter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

@SpringBootTest
@ActiveProfiles("test")
@Import(StayTicketClientAdapterFallbackTest.MockServerConfig.class)
class StayTicketClientAdapterFallbackTest {

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
    private StayTicketClientAdapter adapter;

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
    }

    @AfterEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        mockServer.reset();
    }

    @Test
    void issueEntryTicketFallback_isInvokedWhenCircuitIsOpen() {

        circuitBreakerRegistry
                .circuitBreaker("ticketService")
                .transitionToOpenState();

        EntryTicketInfo result = adapter.issueEntryTicket(
                UUID.randomUUID(),
                "1234ABC",
                Instant.now());

        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isNotNull();
        assertThat(result.barCode()).startsWith("OFFLINE-ENTRY-");
    }

    @Test
    void issueEntryTicket_returnsOfflineTicket_whenServerReturns500() {

        circuitBreakerRegistry
                .circuitBreaker("ticketService")
                .transitionToClosedState();

        mockServer.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        EntryTicketInfo result = adapter.issueEntryTicket(
                UUID.randomUUID(),
                "1234ABC",
                Instant.now());

        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isNotNull();
        assertThat(result.barCode()).startsWith("OFFLINE-ENTRY-");

        mockServer.verify();
    }

    @Test
    void issueExitTicketFallback_isInvokedWhenCircuitIsOpen() {

        circuitBreakerRegistry
                .circuitBreaker("ticketService")
                .transitionToOpenState();

        UUID result = adapter.issueExitTicket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN);

        assertThat(result).isNotNull();
    }

    @Test
    void issueExitTicket_returnsOfflineUuid_whenServerReturns500() {

        circuitBreakerRegistry
                .circuitBreaker("ticketService")
                .transitionToClosedState();

        mockServer.expect(requestTo("http://ticket-service:8080/v1/tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        UUID result = adapter.issueExitTicket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN);

        assertThat(result).isNotNull();

        mockServer.verify();
    }
}