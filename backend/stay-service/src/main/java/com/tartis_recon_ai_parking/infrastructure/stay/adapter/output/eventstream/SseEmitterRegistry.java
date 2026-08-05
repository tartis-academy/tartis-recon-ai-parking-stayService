package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventstream;

import com.tartis_recon_ai_parking.application.stay.dto.SpotStatusChangedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreatedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.TariffChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Estado en memoria por instancia: ver ADR SSE-01 (implica sticky sessions
// o fan-out compartido si se escala a >1 replica).
@Component
public class SseEmitterRegistry implements StayEventStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final String EVENT_STAY_UPDATED = "stay_updated";
    private static final String EVENT_STAY_CREATED = "stay_created";
    private static final String EVENT_TARIFF_UPDATED = "tariff_updated";
    private static final String EVENT_SPOT_STATUS_UPDATED = "spot_status_updated";

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public SseEmitterRegistry(@Value("${sse.emitter.timeout-ms:1800000}") long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public SseEmitter subscribe() {
        UUID id = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        emitter.onCompletion(() -> remove(id));
        emitter.onTimeout(() -> remove(id));
        emitter.onError(ex -> remove(id));

        emitters.put(id, emitter);
        log.info("Cliente SSE conectado ({}), total activos: {}", id, emitters.size());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            // IOException del socket + IllegalStateException del emitter ya completado.
            remove(id);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Override
    public void publish(StayClosedEvent event) {
        broadcast(EVENT_STAY_UPDATED, event.eventId().toString(), event);
    }

    @Override
    public void publish(StayCreatedEvent event) {
        broadcast(EVENT_STAY_CREATED, event.eventId().toString(), event);
    }

    @Override
    public void publish(TariffChangedEvent event) {
        broadcast(EVENT_TARIFF_UPDATED, event.eventId().toString(), event);
    }

    @Override
    public void publish(SpotStatusChangedEvent event) {
        broadcast(EVENT_SPOT_STATUS_UPDATED, event.eventId().toString(), event);
    }


    // Evita que Kong/un balanceador corte la conexion por inactividad.
    @Scheduled(fixedRateString = "${sse.heartbeat.interval-ms:15000}")
    void heartbeat() {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                // IOException del socket + IllegalStateException del emitter ya completado.
                remove(id);
            }
        });
    }

    public int activeCount() {
        return emitters.size();
    }

    // id: = eventId del payload, para que el cliente pueda mandar Last-Event-ID al reconectar.
    private void broadcast(String eventName, String eventId, Object payload) {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().id(eventId).name(eventName).data(payload));
            } catch (Exception e) {
                // IOException del socket + IllegalStateException del emitter ya completado.
                remove(id);
            }
        });
    }

    private void remove(UUID id) {
        SseEmitter removed = emitters.remove(id);
        if (removed != null) {
            log.info("Cliente SSE desconectado ({}), total activos: {}", id, emitters.size());
        }
    }
}
