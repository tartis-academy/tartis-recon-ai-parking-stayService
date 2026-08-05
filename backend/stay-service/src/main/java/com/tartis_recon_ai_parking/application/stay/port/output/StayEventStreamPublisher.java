package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreatedEvent;

// Reenvia por SSE un evento ya publicado en RabbitMQ o generado en el flujo de estancia.
public interface StayEventStreamPublisher {

    void publish(StayClosedEvent event);

    void publish(StayCreatedEvent event);
}

