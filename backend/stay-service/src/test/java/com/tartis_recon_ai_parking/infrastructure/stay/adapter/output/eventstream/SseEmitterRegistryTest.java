package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventstream;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreatedEvent;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(1_800_000L);
    }

    @Test
    @DisplayName("subscribe() registra el emitter y lo cuenta como activo")
    void subscribeRegistersEmitter() {
        assertEquals(0, registry.activeCount());

        SseEmitter emitter = registry.subscribe();

        assertNotNull(emitter);
        assertEquals(1, registry.activeCount());
    }

    @Test
    @DisplayName("cada subscribe() devuelve un emitter distinto")
    void subscribeReturnsDistinctEmitters() {
        SseEmitter first = registry.subscribe();
        SseEmitter second = registry.subscribe();

        assertNotSame(first, second);
        assertEquals(2, registry.activeCount());
    }

    @Test
    @DisplayName("publish() no lanza excepcion con clientes conectados ni sin ellos")
    void publishDoesNotThrow() {
        registry.subscribe();
        registry.subscribe();

        StayClosedEvent event = StayClosedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), "1234ABC",
                Instant.now().minusSeconds(3600), Instant.now(),
                new BigDecimal("5.00"), Instant.now());

        registry.publish(event);

        SseEmitterRegistry empty = new SseEmitterRegistry(1_800_000L);
        empty.publish(event);
    }

    @Test
    @DisplayName("publish(StayCreatedEvent) emite sin lanzar excepcion")
    void publishStayCreatedEventDoesNotThrow() {
        registry.subscribe();

        StayCreatedEvent event = StayCreatedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR,
                UUID.randomUUID(), UUID.randomUUID(), "1234ABC",
                Instant.now(), Instant.now());


        assertDoesNotThrow(() -> registry.publish(event));
    }


    @Test
    @DisplayName("publish() sobre un emitter ya completado no rompe el broadcast y lo descarta")
    void publishRemovesAlreadyCompletedEmitter() {
        SseEmitter dead = registry.subscribe();
        registry.subscribe();

        // complete() marca el emitter sin disparar los callbacks de Spring (no hay
        // handler en un test unitario), asi que sigue en el mapa hasta el envio.
        dead.complete();
        assertEquals(2, registry.activeCount());

        StayClosedEvent event = StayClosedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), "1234ABC",
                Instant.now().minusSeconds(3600), Instant.now(),
                new BigDecimal("5.00"), Instant.now());

        assertDoesNotThrow(() -> registry.publish(event));
        assertEquals(1, registry.activeCount());
    }

    @Test
    @DisplayName("heartbeat() no lanza excepcion con clientes conectados ni sin ellos")
    void heartbeatDoesNotThrow() {
        registry.subscribe();

        registry.heartbeat();

        SseEmitterRegistry empty = new SseEmitterRegistry(1_800_000L);
        empty.heartbeat();
    }

    @Test
    @DisplayName("heartbeat() sobre un emitter ya completado no rompe la ronda y lo descarta")
    void heartbeatRemovesAlreadyCompletedEmitter() {
        SseEmitter dead = registry.subscribe();
        registry.subscribe();

        dead.complete();
        assertEquals(2, registry.activeCount());

        assertDoesNotThrow(() -> registry.heartbeat());
        assertEquals(1, registry.activeCount());
    }
}
