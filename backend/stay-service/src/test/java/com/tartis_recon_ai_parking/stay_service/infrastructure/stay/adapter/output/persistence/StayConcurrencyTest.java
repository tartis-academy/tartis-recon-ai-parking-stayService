package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.ConcurrentStayModificationException;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence.StayRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Condiciones de carrera: pruebas de concurrencia real.
 *
 * <p><b>Por que Postgres de verdad y no H2.</b> El resto de la suite corre sobre
 * H2 en memoria, que es rapido y suficiente para logica. Aqui no vale, por dos
 * motivos que invalidarian la prueba entera:
 *
 * <ol>
 *   <li>El indice unico <b>parcial</b>
 *       ({@code UNIQUE (vehicle_id) WHERE status = 'IN_PROGRESS'}) no existe en
 *       H2. Sobre H2 el doble check-in sencillamente no se detectaria y el test
 *       pasaria en verde sin haber probado nada.</li>
 *   <li>H2 no reproduce el aislamiento ni el bloqueo de filas de Postgres, que
 *       es exactamente el comportamiento que estamos verificando.</li>
 * </ol>
 *
 * <p><b>Por que lanzar hilos de verdad y no simular las excepciones con mocks.</b>
 * Un test con Mockito que hace {@code when(repo.saveAndFlush(...)).thenThrow(...)}
 * comprueba que sabemos escribir un {@code catch}, no que la base de datos vaya a
 * lanzar esa excepcion. La parte que puede estar mal es precisamente esa: que el
 * indice este bien definido, que la version viaje de vuelta hasta el UPDATE, y
 * que Spring traduzca el error del driver al tipo que estamos capturando. Eso
 * solo se ve ejecutandolo.
 *
 * <p>{@link CyclicBarrier} sirve para que los hilos no arranquen escalonados: sin
 * el, el primero suele terminar antes de que el ultimo empiece y no habria
 * carrera que observar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StayConcurrencyTest {

    /** Misma version que la Postgres del docker-compose del servicio. */
    @Container
    @SuppressWarnings("resource") // la cierra Testcontainers al terminar la JVM
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.18-alpine");

    /**
     * Sustituye la configuracion H2 del perfil "test" por la del contenedor.
     * Se activa Flyway (en el resto de la suite esta apagado) porque el indice
     * parcial que se esta probando vive en {@code V2__race_conditions.sql}:
     * generar el esquema con {@code ddl-auto} lo dejaria fuera.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        // validate y no create-drop: si el esquema de Flyway y las entidades se
        // separan, queremos enterarnos aqui y no en produccion.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final int HILOS = 8;

    @Autowired
    private StayPersistence stayPersistence;

    @Autowired
    private StayRepository repository;

    /** Se recrea en cada {@link #ejecutarEnParalelo}; ver {@link #sincronizar()}. */
    private CyclicBarrier barrera;

    @AfterEach
    void limpiar() {
        repository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Carrera de ENTRADA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8 check-in simultaneos del mismo vehiculo: solo uno entra, el resto recibe DuplicateActiveStay")
    void checkInSimultaneoDelMismoVehiculo_soloDebePermitirUnaEstanciaActiva() throws Exception {
        UUID vehicleId = UUID.randomUUID();

        // Cada hilo trae su propia estancia, con id distinto: son peticiones
        // independientes que casualmente hablan del mismo coche. Es justo el
        // escenario de "dos operarios registran el mismo coche en el mismo
        // segundo", que ninguna comprobacion previa en Java puede evitar.
        Resultado resultado = ejecutarEnParalelo(() -> {
            Stay entrada = nuevaEstanciaActiva(vehicleId);
            sincronizar();
            return stayPersistence.save(entrada);
        });

        assertEquals(1, resultado.exitos(),
                "Solo una de las " + HILOS + " peticiones debe conseguir meter el coche");
        assertEquals(HILOS - 1, resultado.contar(DuplicateActiveStayException.class),
                "Las demas deben ser rechazadas como duplicado, no con un error inesperado");
        assertTrue(resultado.otrosFallos().isEmpty(),
                "No deberia haber fallos de otro tipo: " + resultado.otrosFallos());

        // La comprobacion que de verdad importa: el estado final de la BD.
        assertEquals(1, repository.findAll().size(),
                "Solo puede haber quedado una fila: dos estancias activas significan"
                        + " dos plazas ocupadas por el mismo coche");
    }

    @Test
    @DisplayName("El indice es parcial: tras cerrar la estancia, el mismo vehiculo puede volver a entrar")
    void trasFinalizarLaEstancia_elMismoVehiculoPuedeVolverAEntrar() {
        UUID vehicleId = UUID.randomUUID();

        Stay primera = stayPersistence.save(nuevaEstanciaActiva(vehicleId));
        stayPersistence.save(primera.finish(primera.getCheckIn().plusSeconds(3600), new BigDecimal("4.50")));

        // Si alguien "simplificara" el indice quitandole el WHERE, esto
        // reventaria: un coche solo podria entrar una vez en toda su vida.
        Stay segunda = stayPersistence.save(nuevaEstanciaActiva(vehicleId));

        assertNotNull(segunda);
        assertEquals(StayStatus.IN_PROGRESS, segunda.getStatus());
        assertEquals(2, repository.findAll().size());
    }

    // ------------------------------------------------------------------
    // Carrera de SALIDA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8 check-out simultaneos de la misma estancia: solo uno cierra, el resto recibe ConcurrentStayModification")
    void checkOutSimultaneoDeLaMismaEstancia_soloUnoDebeCerrarla() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        stayPersistence.save(nuevaEstanciaActiva(vehicleId));

        Resultado resultado = ejecutarEnParalelo(() -> {
            // Cada hilo lee por su cuenta, como haria CheckOutUseCase, y la
            // lectura va ANTES de la barrera. Asi los ocho se llevan la MISMA
            // version y la carrera queda en el UPDATE, que es donde queremos
            // observarla. Si se leyera despues, los hilos lentos encontrarian la
            // estancia ya cerrada y el test seria intermitente.
            Stay leida = stayPersistence.findByVehicleIdAndStatus(vehicleId, StayStatus.IN_PROGRESS)
                    .orElseThrow(() -> new IllegalStateException("La estancia deberia existir"));

            sincronizar();

            return stayPersistence.save(
                    leida.finish(leida.getCheckIn().plusSeconds(1800), new BigDecimal("2.75")));
        });

        assertEquals(1, resultado.exitos(),
                "Solo un check-out puede prosperar: dos cierres significan dos tickets"
                        + " de salida y dos liberaciones de la misma plaza");
        assertEquals(HILOS - 1, resultado.contar(ConcurrentStayModificationException.class),
                "Los perdedores deben salir como conflicto de concurrencia (409), no como 500");
        assertTrue(resultado.otrosFallos().isEmpty(),
                "No deberia haber fallos de otro tipo: " + resultado.otrosFallos());

        List<Stay> finales = repository.findAll().stream()
                .map(entidad -> stayPersistence.findById(entidad.getUniqueId()).orElseThrow())
                .toList();

        assertEquals(1, finales.size());
        assertEquals(StayStatus.FINISHED, finales.get(0).getStatus());
        assertEquals(Long.valueOf(1L), finales.get(0).getVersion(),
                "La version debe haberse incrementado exactamente una vez: si hubiera subido"
                        + " mas de una, varios UPDATE habrian pasado y el ultimo habria pisado al resto");
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static Stay nuevaEstanciaActiva(UUID vehicleId) {
        return Stay.checkIn(
                UUID.randomUUID(),
                vehicleId,
                VehicleType.CAR,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-03T08:00:00Z"));
    }

    /**
     * Punto de encuentro. Cada test llama a este metodo justo antes de la
     * operacion que quiere poner en carrera; hasta que no llegan los
     * {@link #HILOS} hilos, ninguno sigue.
     *
     * <p>Es lo que convierte el test en una carrera de verdad. Sin barrera, el
     * primer hilo suele terminar antes de que el ultimo empiece y las
     * operaciones acaban ejecutandose en fila, sin solaparse nunca.
     */
    private void sincronizar() throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
    }

    /**
     * Lanza {@link #HILOS} copias de la misma operacion. La operacion decide en
     * que punto exacto se sincronizan los hilos llamando a {@link #sincronizar()}.
     */
    private Resultado ejecutarEnParalelo(Callable<Stay> operacion) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        barrera = new CyclicBarrier(HILOS);
        AtomicInteger exitos = new AtomicInteger();

        try {
            List<Future<Throwable>> futuros = pool.invokeAll(
                    Collections.nCopies(HILOS, (Callable<Throwable>) () -> {
                        try {
                            operacion.call();
                            exitos.incrementAndGet();
                            return null;
                        } catch (Throwable fallo) {
                            // Se recoge en vez de propagarse (incluido un fallo
                            // de la barrera) para que el test informe de que ha
                            // pasado en lugar de quedarse colgado.
                            return fallo;
                        }
                    }));

            List<Throwable> fallos = new ArrayList<>();
            for (Future<Throwable> futuro : futuros) {
                Throwable fallo = futuro.get(30, TimeUnit.SECONDS);
                if (fallo != null) {
                    fallos.add(fallo);
                }
            }
            return new Resultado(exitos.get(), fallos);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Resultado(int exitos, List<Throwable> fallos) {

        long contar(Class<? extends Throwable> tipo) {
            return fallos.stream().filter(tipo::isInstance).count();
        }

        List<String> otrosFallos() {
            return fallos.stream()
                    .filter(f -> !(f instanceof DuplicateActiveStayException)
                            && !(f instanceof ConcurrentStayModificationException))
                    .map(f -> f.getClass().getSimpleName() + ": " + f.getMessage())
                    .toList();
        }
    }
}
