package com.tartis_recon_ai_parking.infrastructure.config;

import com.tartis_recon_ai_parking.domain.stay.exception.NoActiveTariffException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.TariffServiceException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RES-02: blinda la configuracion de los circuit breakers.
 *
 * <p>Un typo en application.yml (un nombre de instancia mal escrito, una
 * excepcion que ya no existe) no rompe el arranque: Resilience4j se limita a
 * crear la instancia con los valores por defecto, y el circuito se queda mudo
 * sin que nadie se entere hasta que un servicio se cae en produccion. Estos
 * tests convierten ese fallo silencioso en un fallo de build.
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerConfigurationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Criterio de aceptacion: una instancia por servicio destino. Los cuatro
     * nombres son exactamente los servicios a los que llama stay-service.
     */
    @Test
    void shouldRegisterOneCircuitBreakerPerDestinationService() {
        assertThat(circuitBreakerRegistry.getAllCircuitBreakers())
                .extracting(cb -> cb.getName())
                .containsExactlyInAnyOrder(
                        "vehicleService", "spotService", "tariffService", "ticketService");
    }

    /**
     * Criterios de aceptacion: sliding-window-size, failure-rate-threshold y
     * wait-duration-in-open-state configurados, con valores propios por
     * instancia (no heredados en bloque de la config por defecto).
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "vehicleService, 20, 10, 50, 10000",
            "spotService,    20, 10, 50, 10000",
            "tariffService,  10,  5, 50, 20000",
            "ticketService,  10,  5, 40, 30000"
    })
    void shouldConfigureWindowThresholdAndWaitDurationPerInstance(
            String instanceName,
            int expectedSlidingWindowSize,
            int expectedMinimumNumberOfCalls,
            float expectedFailureRateThreshold,
            long expectedWaitDurationMillis) {

        CircuitBreakerConfig config = circuitBreakerRegistry
                .circuitBreaker(instanceName)
                .getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(expectedSlidingWindowSize);
        assertThat(config.getFailureRateThreshold()).isEqualTo(expectedFailureRateThreshold);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(expectedWaitDurationMillis);

        // minimum-number-of-calls vale 100 por defecto: si se olvida, una
        // ventana de 10 o 20 llamadas jamas alcanza el minimo y el circuito
        // no llega a abrirse nunca.
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(expectedMinimumNumberOfCalls);
    }

    /**
     * El circuito mide salud del servicio destino, no reglas de negocio.
     * "No hay plazas libres" (RN-01) o "no hay tarifa activa" (IN-08) son
     * respuestas correctas de un servicio perfectamente sano: si contasen
     * como fallo, un parking lleno abriria el circuito de spot-service y
     * bloquearia los check-in que si tenian plaza.
     */
    @Test
    void shouldNotCountBusinessExceptionsAsFailures() {
        Predicate<Throwable> spot = circuitBreakerRegistry
                .circuitBreaker("spotService")
                .getCircuitBreakerConfig()
                .getRecordExceptionPredicate();

        assertThat(spot.test(new SpotServiceException("spot-service no responde"))).isTrue();
        assertThat(spot.test(new NoAvailableSpotException("no hay plazas libres"))).isFalse();

        Predicate<Throwable> tariff = circuitBreakerRegistry
                .circuitBreaker("tariffService")
                .getCircuitBreakerConfig()
                .getRecordExceptionPredicate();

        assertThat(tariff.test(new TariffServiceException("tariff-service no responde"))).isTrue();
        assertThat(tariff.test(new NoActiveTariffException("sin tarifa activa"))).isFalse();

        Predicate<Throwable> vehicle = circuitBreakerRegistry
                .circuitBreaker("vehicleService")
                .getCircuitBreakerConfig()
                .getRecordExceptionPredicate();

        assertThat(vehicle.test(new VehicleServiceException("vehicle-service no responde"))).isTrue();
        assertThat(vehicle.test(new InvalidStayException("matricula invalida"))).isFalse();
    }

    /**
     * Sin esto, el circuito sale de OPEN solo cuando llega una llamada nueva:
     * de madrugada podria quedarse abierto horas con el servicio destino ya
     * recuperado.
     */
    @Test
    void shouldTransitionFromOpenToHalfOpenAutomatically() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker ->
                assertThat(circuitBreaker.getCircuitBreakerConfig()
                        .isAutomaticTransitionFromOpenToHalfOpenEnabled())
                        .as("instancia %s", circuitBreaker.getName())
                        .isTrue());
    }
}