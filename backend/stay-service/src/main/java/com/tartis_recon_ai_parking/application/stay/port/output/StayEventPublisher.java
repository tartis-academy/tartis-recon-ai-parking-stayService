package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;

public interface StayEventPublisher {

    /**
     * Publica el evento StayClosedEvent de forma asíncrona hacia RabbitMQ.
     */
    void publish(StayClosedEvent event);
}