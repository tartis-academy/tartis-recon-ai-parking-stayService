package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;

// Reenvia por SSE un evento ya publicado en RabbitMQ.
public interface StayEventStreamPublisher {

    void publish(StayClosedEvent event);
}
