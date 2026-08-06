package com.tartis_recon_ai_parking.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RES-09: el estado de los circuit breakers tiene que poder ENSENARSE en vivo
 * durante la demo, no solo existir en memoria.
 *
 * <p>Que cubre cada test de circuit breaker, para no duplicar:
 * <ul>
 *   <li>{@code CircuitBreakerConfigurationTest}: las instancias estan bien
 *       configuradas (ventanas, umbrales, excepciones que cuentan).</li>
 *   <li>{@code CircuitBreakerBehaviourTest}: abren y cierran cuando deben.</li>
 *   <li><b>Esta clase</b>: lo que hace falta para que ese estado sea
 *       <i>observable desde fuera</i> el dia de la demo.</li>
 * </ul>
 *
 * <p>El fallo que previene: alguien edita
 * {@code management.endpoints.web.exposure.include} y se deja fuera
 * "circuitbreakers". Los otros dos tests seguirian en verde —los circuitos
 * funcionan igual de bien— y el problema no aparece hasta que abres el
 * navegador delante del cliente y recibes un 404.
 *
 * <p><b>Por que no se prueba por HTTP.</b> La version por MockMvc contra
 * {@code /actuator/circuitbreakers} no funciona: con {@code @SpringBootTest} en
 * entorno MOCK el handler mapping de actuator no queda registrado en el
 * DispatcherServlet del test, la peticion cae en el handler de recursos
 * estaticos y termina en {@code NoResourceFoundException} -> 500. Probar la
 * capa HTTP exigiria levantar servidor real ({@code RANDOM_PORT}) y un JWT
 * autentico, que es justo lo que hace el guion manual de
 * {@code docs/demo-circuit-breakers.md}. Aqui se blinda la configuracion, que
 * es la parte que se rompe por descuido; la comprobacion end-to-end queda en
 * el ensayo previo a la demo.
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerActuatorExposureTest {

    /** Instancia elegida para la demo: la de wait-duration mas largo (20s). */
    private static final String DEMO_CIRCUIT = "tariffService";

    /**
     * Configuracion REAL de despliegue. Se lee del fichero y no del
     * {@code Environment} de Spring a proposito: {@code src/test/resources/
     * application.properties} tapa al de {@code src/main} en el classpath de
     * test (el propio fichero de test lo advierte en un comentario), asi que en
     * un test el Environment nunca ve la config que se despliega. Preguntarle a
     * el daria un falso verde: es exactamente lo que hay que blindar aqui.
     */
    private static final Path PRODUCTION_CONFIG =
            Path.of("src", "main", "resources", "application.properties");

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * El registry es un bean de contexto compartido entre clases de test: si
     * otra deja un circuito abierto, contamina a esta. Se limpia ANTES de cada
     * test (mismo patron que {@code CircuitBreakerBehaviourTest}) y no despues,
     * porque lo que hay que garantizar es el estado de partida.
     */
    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("La config desplegada sigue exponiendo los endpoints de circuit breaker")
    void shouldExposeCircuitBreakerEndpoints() throws IOException {
        String exposed = productionProperties()
                .getProperty("management.endpoints.web.exposure.include", "");

        // circuitbreakers da la foto del estado actual; circuitbreakerevents el
        // historico de transiciones, que es el que se ensena en la demo.
        assertThat(exposed)
                .as("management.endpoints.web.exposure.include debe seguir publicando "
                        + "los endpoints de circuit breaker (RES-09)")
                .contains("circuitbreakers")
                .contains("circuitbreakerevents");
    }

    @Test
    @DisplayName("La config desplegada mantiene activo el indicador de salud de los circuitos")
    void shouldKeepCircuitBreakerHealthIndicatorEnabled() throws IOException {
        assertThat(productionProperties().getProperty("management.health.circuitbreakers.enabled"))
                .as("sin esto el estado del circuito desaparece de /actuator/health")
                .isEqualTo("true");
    }

    private static Properties productionProperties() throws IOException {
        assertThat(PRODUCTION_CONFIG)
                .as("no se encuentra la config de despliegue; "
                        + "surefire deberia ejecutarse con el modulo como directorio de trabajo")
                .exists();

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(PRODUCTION_CONFIG)) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    @DisplayName("Los tres estados del criterio de aceptacion son observables: CLOSED, OPEN, HALF_OPEN")
    void shouldExposeTheThreeDemoStates() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(DEMO_CIRCUIT);

        // El endpoint /actuator/circuitbreakers serializa exactamente este
        // getState(), asi que comprobar el registry es comprobar lo que vera
        // el publico en la demo.
        assertThat(circuitBreaker.getState())
                .as("estado de partida")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // En produccion esta transicion la dispara sola
        // automatic-transition-from-open-to-half-open-enabled al cumplirse
        // wait-duration-in-open-state (20s en tariffService); aqui se fuerza
        // para no meter una espera real en el build.
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        circuitBreaker.transitionToClosedState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
