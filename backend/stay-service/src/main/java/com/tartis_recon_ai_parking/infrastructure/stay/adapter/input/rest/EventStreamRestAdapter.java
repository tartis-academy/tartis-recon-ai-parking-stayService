package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.infrastructure.config.SecurityConfig;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventstream.SseEmitterRegistry;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Solo ADMIN/OPERARIO: el broadcast no filtra por usuario (ver ADR SSE-01).
@RestController
@RequestMapping(SecurityConfig.SSE_PATH)
public class EventStreamRestAdapter {

    private final SseEmitterRegistry registry;

    public EventStreamRestAdapter(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERARIO')")
    public SseEmitter subscribe() {
        return registry.subscribe();
    }
}
