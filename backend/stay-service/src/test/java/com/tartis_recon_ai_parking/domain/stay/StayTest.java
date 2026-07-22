package com.tartis_recon_ai_parking.domain.stay;

import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// assertThat y assertThatThrownBy: Son metodos estaticos de AssertJ que permiten escribir
// comprobaciones fluidas, faciles de leer y autoexplicativas sobre los resultados.
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Pruebas unitarias de la entidad de dominio Stay. Son Java puro: no arrancan Spring
// ni tocan base de datos, por lo que validan directamente los invariantes de negocio
// (IN-13 a IN-19, IN-34) y el comportamiento de dominio (RN-06).
class StayTest {

    // Datos de apoyo reutilizados por los tests para no repetir literales en cada caso.
    private static final UUID ID = UUID.randomUUID();
    private static final UUID SPOT_ID = UUID.randomUUID();
    private static final UUID TARIFF_ID = UUID.randomUUID();
    private static final String PLATE = "1234ABC";
    private static final Instant CHECK_IN = Instant.parse("2026-07-22T10:00:00Z");

    // ------------------------------------------------------------------
    // Fabricas y estado inicial
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Debe crear una estancia en curso (IN_PROGRESS) con checkOut e importe nulos")
    void shouldCreateStayInProgress() {
        // QUE HACE:
        // Crea una estancia nueva con la fabrica checkIn(), que representa un vehiculo
        // que acaba de entrar en el parking.
        Stay stay = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);

        // QUE DEBERIA HACER:
        // Debe quedar en estado IN_PROGRESS, activa, sin hora de salida ni importe (IN-14),
        // y los getters deben devolver los valores con los que se creo.
        assertThat(stay.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
        assertThat(stay.isActive()).isTrue();
        assertThat(stay.getCheckOut()).isNull();
        assertThat(stay.getTotalAmount()).isNull();
        assertThat(stay.getId()).isEqualTo(ID);
        assertThat(stay.getSpotId()).isEqualTo(SPOT_ID);
        assertThat(stay.getTariffId()).isEqualTo(TARIFF_ID);
        assertThat(stay.getVehicleType()).isEqualTo(VehicleType.CAR);
    }

    @Test
    @DisplayName("Debe normalizar la matricula recortando espacios y pasando a mayusculas")
    void shouldNormalizePlate() {
        // QUE HACE:
        // Crea una estancia con una matricula en minusculas y con espacios alrededor.
        Stay stay = Stay.checkIn(ID, "  1234abc  ", VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);

        // QUE DEBERIA HACER:
        // La entidad debe almacenar la matricula normalizada (trim + mayusculas), garantizando
        // que la misma matricula escrita de dos formas distintas se guarde igual.
        assertThat(stay.getPlate()).isEqualTo("1234ABC");
    }

    @Test
    @DisplayName("Debe reconstruir una estancia finalizada desde persistencia revalidando invariantes")
    void shouldRestoreFinishedStay() {
        // QUE HACE:
        // Usa la fabrica restore(), que emplea el adaptador de persistencia para reconstruir
        // una estancia ya cerrada a partir de los datos de la base de datos.
        Instant checkOut = CHECK_IN.plus(30, ChronoUnit.MINUTES);
        Stay stay = Stay.restore(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID,
                CHECK_IN, checkOut, new BigDecimal("2.50"), StayStatus.FINISHED);

        // QUE DEBERIA HACER:
        // Debe reconstruirse con exito con todos sus datos, ya que son coherentes con los
        // invariantes; restore revalida, de modo que datos corruptos se detectarian aqui.
        assertThat(stay.getStatus()).isEqualTo(StayStatus.FINISHED);
        assertThat(stay.getCheckOut()).isEqualTo(checkOut);
        assertThat(stay.getTotalAmount()).isEqualByComparingTo("2.50");
        assertThat(stay.isActive()).isFalse();
    }

    // ------------------------------------------------------------------
    // Transiciones de estado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("finish() debe devolver una nueva instancia FINISHED sin mutar la original (IN-19)")
    void shouldFinishStayReturningNewImmutableInstance() {
        // QUE HACE:
        // Parte de una estancia en curso y la cierra con finish(), aportando hora de salida e importe.
        Stay inProgress = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Instant checkOut = CHECK_IN.plus(1, ChronoUnit.HOURS);

        Stay finished = inProgress.finish(checkOut, new BigDecimal("3.00"));

        // QUE DEBERIA HACER:
        // Debe devolver una instancia NUEVA en estado FINISHED con salida e importe, mientras que
        // la original permanece intacta en IN_PROGRESS (la entidad es inmutable, append-only).
        assertThat(finished.getStatus()).isEqualTo(StayStatus.FINISHED);
        assertThat(finished.getCheckOut()).isEqualTo(checkOut);
        assertThat(finished.getTotalAmount()).isEqualByComparingTo("3.00");
        assertThat(inProgress.getStatus()).isEqualTo(StayStatus.IN_PROGRESS);
        assertThat(inProgress.getCheckOut()).isNull();
    }

    @Test
    @DisplayName("cancel() debe devolver una nueva instancia CANCELLED sin importe")
    void shouldCancelStay() {
        // QUE HACE:
        // Anula una estancia en curso (caso CB-06: el vehiculo retrocede sin llegar a entrar).
        Stay inProgress = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Instant cancelledAt = CHECK_IN.plus(2, ChronoUnit.MINUTES);

        Stay cancelled = inProgress.cancel(cancelledAt);

        // QUE DEBERIA HACER:
        // Debe quedar en CANCELLED con la hora de anulacion como checkOut y SIN importe,
        // porque una anulacion no genera cobro.
        assertThat(cancelled.getStatus()).isEqualTo(StayStatus.CANCELLED);
        assertThat(cancelled.getCheckOut()).isEqualTo(cancelledAt);
        assertThat(cancelled.getTotalAmount()).isNull();
    }

    @Test
    @DisplayName("Debe lanzar InvalidStayException al finalizar una estancia ya terminal (IN-19)")
    void shouldThrowWhenFinishingTerminalStay() {
        // QUE HACE:
        // Cierra una estancia y despues intenta volver a cerrarla.
        Stay finished = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN)
                .finish(CHECK_IN.plus(1, ChronoUnit.HOURS), new BigDecimal("3.00"));

        // QUE DEBERIA HACER:
        // Debe fallar: un estado terminal (FINISHED) es inmutable y no admite nuevas transiciones.
        assertThatThrownBy(() -> finished.finish(CHECK_IN.plus(2, ChronoUnit.HOURS), new BigDecimal("5.00")))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-19");
    }

    @Test
    @DisplayName("Debe lanzar InvalidStayException al anular una estancia ya terminal (IN-19)")
    void shouldThrowWhenCancellingTerminalStay() {
        // QUE HACE:
        // Anula una estancia y despues intenta anularla otra vez.
        Stay cancelled = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN)
                .cancel(CHECK_IN.plus(1, ChronoUnit.MINUTES));

        // QUE DEBERIA HACER:
        // Debe fallar por la misma razon: CANCELLED es terminal e inmutable.
        assertThatThrownBy(() -> cancelled.cancel(CHECK_IN.plus(2, ChronoUnit.MINUTES)))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-19");
    }

    // ------------------------------------------------------------------
    // Invariantes de validacion (IN-14, IN-15, IN-16, IN-34)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("IN-15: debe lanzar excepcion si la hora de salida es anterior a la de entrada")
    void shouldThrowWhenCheckOutBeforeCheckIn() {
        // QUE HACE:
        // Intenta finalizar una estancia con una hora de salida anterior a la de entrada.
        Stay inProgress = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Instant before = CHECK_IN.minus(5, ChronoUnit.MINUTES);

        // QUE DEBERIA HACER:
        // Debe lanzar InvalidStayException por incoherencia temporal (checkOut < checkIn).
        assertThatThrownBy(() -> inProgress.finish(before, new BigDecimal("3.00")))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-15");
    }

    @Test
    @DisplayName("IN-16: debe lanzar excepcion si una estancia finalizada tiene importe cero o negativo")
    void shouldThrowWhenFinishedAmountIsNotPositive() {
        // QUE HACE:
        // Intenta finalizar una estancia con importe 0 y luego con importe negativo.
        Stay inProgress = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Instant checkOut = CHECK_IN.plus(1, ChronoUnit.HOURS);

        // QUE DEBERIA HACER:
        // Ambos casos deben lanzar InvalidStayException: una estancia finalizada siempre cobra > 0.
        assertThatThrownBy(() -> inProgress.finish(checkOut, BigDecimal.ZERO))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-16");
        assertThatThrownBy(() -> inProgress.finish(checkOut, new BigDecimal("-1.00")))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-16");
    }

    @Test
    @DisplayName("IN-14/IN-16: restore debe rechazar una estancia FINISHED sin salida o sin importe")
    void shouldRejectInconsistentFinishedOnRestore() {
        // QUE HACE:
        // Intenta reconstruir desde persistencia una estancia FINISHED incoherente:
        // primero sin hora de salida, despues sin importe.
        // QUE DEBERIA HACER:
        // restore revalida los invariantes, por lo que ambos casos deben lanzar InvalidStayException.
        assertThatThrownBy(() -> Stay.restore(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID,
                CHECK_IN, null, new BigDecimal("2.00"), StayStatus.FINISHED))
                .isInstanceOf(InvalidStayException.class);

        assertThatThrownBy(() -> Stay.restore(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID,
                CHECK_IN, CHECK_IN.plus(1, ChronoUnit.HOURS), null, StayStatus.FINISHED))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-16");
    }

    @Test
    @DisplayName("IN-14: restore debe rechazar una estancia IN_PROGRESS que tenga hora de salida")
    void shouldRejectInProgressWithCheckOutOnRestore() {
        // QUE HACE:
        // Intenta reconstruir una estancia en curso que, incoherentemente, tiene hora de salida.
        // QUE DEBERIA HACER:
        // Debe fallar: checkOut es nulo si y solo si el estado es IN_PROGRESS (IN-14).
        assertThatThrownBy(() -> Stay.restore(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID,
                CHECK_IN, CHECK_IN.plus(1, ChronoUnit.HOURS), null, StayStatus.IN_PROGRESS))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("IN-14");
    }

    @Test
    @DisplayName("IN-13/IN-34: debe lanzar excepcion si algun campo obligatorio es nulo")
    void shouldThrowWhenRequiredFieldIsNull() {
        // QUE HACE:
        // Intenta crear estancias con campos obligatorios a null (spotId, tariffId, checkIn).
        // QUE DEBERIA HACER:
        // Cada caso debe lanzar InvalidStayException indicando el campo que falta, impidiendo
        // construir una entidad invalida.
        assertThatThrownBy(() -> Stay.checkIn(ID, PLATE, VehicleType.CAR, null, TARIFF_ID, CHECK_IN))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("spotId");
        assertThatThrownBy(() -> Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, null, CHECK_IN))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("tariffId");
        assertThatThrownBy(() -> Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, null))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("checkIn");
    }

    @Test
    @DisplayName("Debe lanzar excepcion si la matricula es nula o esta vacia")
    void shouldThrowWhenPlateIsBlank() {
        // QUE HACE:
        // Intenta crear estancias con matricula null y con matricula formada solo por espacios.
        // QUE DEBERIA HACER:
        // Ambos casos deben lanzar InvalidStayException: la matricula es obligatoria y no vacia.
        assertThatThrownBy(() -> Stay.checkIn(ID, null, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("plate");
        assertThatThrownBy(() -> Stay.checkIn(ID, "   ", VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("vacia");
    }

    @Test
    @DisplayName("Debe lanzar excepcion si la matricula supera los 15 caracteres")
    void shouldThrowWhenPlateTooLong() {
        // QUE HACE:
        // Intenta crear una estancia con una matricula de 16 caracteres.
        // QUE DEBERIA HACER:
        // Debe lanzar InvalidStayException por superar la longitud maxima admitida (15).
        assertThatThrownBy(() -> Stay.checkIn(ID, "ABCDEFGHIJKLMNOP", VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("15");
    }

    // ------------------------------------------------------------------
    // Comportamiento de dominio: calculo de minutos (RN-06)
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(longs = { 1, 59, 60, 61, 120 })
    @DisplayName("RN-06: parkedMinutesUntil debe redondear los minutos hacia arriba, a favor del sistema")
    void shouldRoundParkedMinutesUp(long seconds) {
        // QUE HACE:
        // Calcula los minutos estacionados para distintas duraciones en segundos.
        Stay stay = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Instant until = CHECK_IN.plusSeconds(seconds);

        // QUE DEBERIA HACER:
        // Debe redondear SIEMPRE hacia arriba (1s->1min, 60s->1min, 61s->2min), tal y como
        // exige la regla RN-06 de redondear a favor del sistema.
        long expected = (seconds + 59L) / 60L;
        assertThat(stay.parkedMinutesUntil(until)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Debe lanzar excepcion si se piden minutos con un instante anterior al check-in")
    void shouldThrowWhenUntilBeforeCheckIn() {
        // QUE HACE:
        // Pide los minutos estacionados pasando un instante anterior a la hora de entrada.
        Stay stay = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);

        // QUE DEBERIA HACER:
        // Debe lanzar InvalidStayException: no se puede calcular una duracion negativa.
        assertThatThrownBy(() -> stay.parkedMinutesUntil(CHECK_IN.minusSeconds(1)))
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    @DisplayName("parkedMinutes() debe lanzar excepcion si la estancia sigue en curso")
    void shouldThrowParkedMinutesWhenInProgress() {
        // QUE HACE:
        // Pide los minutos totales (basados en checkOut) de una estancia todavia abierta.
        Stay stay = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);

        // QUE DEBERIA HACER:
        // Debe lanzar InvalidStayException porque una estancia en curso aun no tiene hora de salida.
        assertThatThrownBy(stay::parkedMinutes)
                .isInstanceOf(InvalidStayException.class)
                .hasMessageContaining("en curso");
    }

    @Test
    @DisplayName("parkedMinutes() debe calcular los minutos de una estancia ya cerrada")
    void shouldComputeParkedMinutesWhenFinished() {
        // QUE HACE:
        // Cierra una estancia de 90 minutos exactos y consulta parkedMinutes().
        Instant checkOut = CHECK_IN.plus(90, ChronoUnit.MINUTES);
        Stay finished = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN)
                .finish(checkOut, new BigDecimal("4.50"));

        // QUE DEBERIA HACER:
        // Debe devolver 90 minutos, calculados sobre su propia hora de salida.
        assertThat(finished.parkedMinutes()).isEqualTo(90L);
    }

    // ------------------------------------------------------------------
    // Identidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Dos estancias con el mismo id deben ser iguales aunque cambie su estado")
    void shouldBeEqualById() {
        // QUE HACE:
        // Compara una estancia en curso con su version finalizada (mismo id, distinto estado).
        Stay inProgress = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Stay finished = inProgress.finish(CHECK_IN.plus(1, ChronoUnit.HOURS), new BigDecimal("3.00"));

        // QUE DEBERIA HACER:
        // La identidad de la entidad se basa solo en el id, de modo que ambas deben ser iguales
        // y compartir hashCode, pese a estar en estados diferentes.
        assertThat(finished).isEqualTo(inProgress);
        assertThat(finished.hashCode()).isEqualTo(inProgress.hashCode());
    }

    @Test
    @DisplayName("Dos estancias con distinto id no deben ser iguales")
    void shouldNotBeEqualWhenDifferentId() {
        // QUE HACE:
        // Crea dos estancias con identicos datos salvo el id.
        Stay a = Stay.checkIn(ID, PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);
        Stay b = Stay.checkIn(UUID.randomUUID(), PLATE, VehicleType.CAR, SPOT_ID, TARIFF_ID, CHECK_IN);

        // QUE DEBERIA HACER:
        // Deben considerarse distintas: la igualdad depende exclusivamente del id.
        assertThat(a).isNotEqualTo(b);
    }
}
