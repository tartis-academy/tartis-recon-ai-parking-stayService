package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.dto;

import java.time.Instant;

/**
 * Cuerpo de error del contrato {@code openapi.yml}, comun a todos los endpoints.
 */
public record ErrorResponse(Instant timestamp,
                            int status,
                            String error,
                            String message,
                            String path) {
}
