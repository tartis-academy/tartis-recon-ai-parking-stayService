package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RES-04: cubre especificamente occupySpotFallback() de
 * StaySpotClientAdapter, que CircuitBreakerBehaviourTest y
 * CircuitBreakerConfigurationTest no ejercitan (esos manipulan el
 * CircuitBreaker directamente, nunca llaman al bean real del adaptador).
 */
@SpringBootTest
@ActiveProfiles("test")
class StaySpotClientAdapterFallbackTest {

    @Autowired
    private StaySpotClientAdapter adapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void occupySpotFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("spotService").transitionToOpenState();

        assertThatThrownBy(() -> adapter.occupySpot(VehicleType.CAR))
                .isInstanceOf(SpotServiceException.class)
                .hasMessageContaining("circuito abierto");
    }
}