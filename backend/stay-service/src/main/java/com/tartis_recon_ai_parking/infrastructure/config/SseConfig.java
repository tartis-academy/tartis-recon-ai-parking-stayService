package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el heartbeat periodico de SseEmitterRegistry (SSE-02).
 */
@Configuration
@EnableScheduling
public class SseConfig {
}
