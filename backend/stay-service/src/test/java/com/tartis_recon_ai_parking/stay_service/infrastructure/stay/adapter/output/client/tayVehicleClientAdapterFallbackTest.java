package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RES-03: cubre especificamente los 3 metodos fallback de
 * StayVehicleClientAdapter, que CircuitBreakerBehaviourTest y
 * CircuitBreakerConfigurationTest no ejercitan - esos manipulan el
 * CircuitBreaker directamente (onError/getState), nunca llaman al bean real
 * del adaptador, asi que el codigo dentro de los fallback se queda sin
 * cubrir aunque la configuracion este perfectamente probada.
 *
 * <p>Aqui forzamos el circuito a OPEN con transitionToOpenState() (sin
 * necesidad de simular fallos de conexion real) y llamamos al bean
 * autowireado - con AOP activo de verdad - para confirmar que
 * CallNotPermittedException dispara el fallback correspondiente.
 */
@SpringBootTest
@ActiveProfiles("test")
class StayVehicleClientAdapterFallbackTest {

    @Autowired
    private StayVehicleClientAdapter adapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void findByPlateFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("vehicleService").transitionToOpenState();

        assertThatThrownBy(() -> adapter.findByPlate("1234BCD"))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");
    }

    @Test
    void getOrCreateVehicleFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("vehicleService").transitionToOpenState();

        assertThatThrownBy(() -> adapter.getOrCreateVehicle("1234BCD", VehicleType.CAR))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");
    }

    @Test
    void findByIdFallback_isInvokedWhenCircuitIsOpen() {
        circuitBreakerRegistry.circuitBreaker("vehicleService").transitionToOpenState();

        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.findById(id))
                .isInstanceOf(VehicleServiceException.class)
                .hasMessageContaining("circuito abierto");
    }
}