package com.tartis_recon_ai_parking.infrastructure.config;

import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.TariffServiceException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RES-02: comprueba el COMPORTAMIENTO de la configuracion, no solo sus valores.
 *
 * <p>No arranca ningun servidor ni llama a spot-service: alimenta el circuito
 * directamente con resultados simulados, que es como Resilience4j permite
 * probar transiciones de estado sin depender de la red.
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerBehaviourTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * El registro es un bean compartido y el contexto de Spring se reutiliza
     * entre clases de test: sin este reset, un circuito que este test deja
     * abierto haria fallar a otro que se ejecute despues.
     */
    @AfterEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    /**
     * spotService: ventana 20, minimo 10 llamadas, umbral 50 %.
     * Con 10 fallos seguidos se alcanza el minimo con un 100 % de fallo,
     * asi que el circuito debe abrirse.
     */
    @Test
    void shouldOpenAfterReachingTheFailureThreshold() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("spotService");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 9 fallos: aun no se ha alcanzado el minimo de llamadas, el circuito
        // no tiene evidencia suficiente para decidir y sigue cerrado.
        for (int i = 0; i < 9; i++) {
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS,
                    new SpotServiceException("spot-service no responde"));
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // El decimo cruza el minimo con un 100 % de fallo: se abre.
        circuitBreaker.onError(0, TimeUnit.MILLISECONDS,
                new SpotServiceException("spot-service no responde"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Con el circuito abierto se deja de llamar al servicio destino: las
        // peticiones se rechazan de inmediato en vez de esperar al timeout.
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
    }

    /**
     * El escenario que motiva `record-exceptions`: un parking lleno. RN-01
     * lanza NoAvailableSpotException una y otra vez, pero spot-service esta
     * perfectamente sano, asi que el circuito no debe abrirse.
     */
    @Test
    void shouldStayClosedWhenTheParkingIsFull() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("spotService");

        for (int i = 0; i < 30; i++) {
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS,
                    new NoAvailableSpotException("no hay plazas libres"));
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
    }

    /**
     * Instancias independientes: hundir spot-service no debe dejar sin servicio
     * a los otros tres destinos.
     */
    @Test
    void shouldNotAffectOtherDestinationServices() {
        CircuitBreaker spot = circuitBreakerRegistry.circuitBreaker("spotService");

        for (int i = 0; i < 10; i++) {
            spot.onError(0, TimeUnit.MILLISECONDS,
                    new SpotServiceException("spot-service no responde"));
        }
        assertThat(spot.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(circuitBreakerRegistry.circuitBreaker("vehicleService").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreakerRegistry.circuitBreaker("tariffService").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreakerRegistry.circuitBreaker("ticketService").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    /**
     * RES-05 (ADR 002): tariffService es el circuito que protege el check-out.
     * Config propia: ventana 10, minimo 5 llamadas, umbral 50 %. Con 5 fallos
     * seguidos (100 % de fallo sobre el minimo) el circuito debe abrirse; a
     * partir de ahi el check-out falla rapido (CallNotPermittedException -> 503)
     * en vez de esperar el timeout contra tariff-service.
     */
    @Test
    void tariffServiceShouldOpenAfterFiveConsecutiveFailures() {
        CircuitBreaker tariff = circuitBreakerRegistry.circuitBreaker("tariffService");

        assertThat(tariff.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 4 fallos: aun por debajo del minimo de 5 llamadas, sin evidencia
        // suficiente, el circuito sigue cerrado.
        for (int i = 0; i < 4; i++) {
            tariff.onError(0, TimeUnit.MILLISECONDS,
                    new TariffServiceException("tariff-service no responde"));
        }
        assertThat(tariff.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // El quinto fallo alcanza el minimo con 100 % de error: se abre.
        tariff.onError(0, TimeUnit.MILLISECONDS,
                new TariffServiceException("tariff-service no responde"));
        assertThat(tariff.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Con el circuito abierto, las llamadas ya no se permiten: esto es lo
        // que en produccion se traduce en CallNotPermittedException.
        assertThat(tariff.tryAcquirePermission()).isFalse();
    }
}