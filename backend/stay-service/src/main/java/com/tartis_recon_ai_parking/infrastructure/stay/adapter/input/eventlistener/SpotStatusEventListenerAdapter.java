package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import com.tartis_recon_ai_parking.application.stay.dto.SpotStatusChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// SSE-06: no hay logica de dominio que aplicar. El unico trabajo es reenviar
// por SSE el evento que ya publico spot-service en RabbitMQ.
@Component
public class SpotStatusEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SpotStatusEventListenerAdapter.class);

    private final StayEventStreamPublisher eventStreamPublisher;

    public SpotStatusEventListenerAdapter(StayEventStreamPublisher eventStreamPublisher) {
        this.eventStreamPublisher = eventStreamPublisher;
    }

    @RabbitListener(queues = RabbitMQConfig.SPOT_STATUS_CHANGED_QUEUE)
    public void handleSpotStatusChangedEvent(SpotStatusChangedEvent event) {
        log.info("Recibido SpotStatusChangedEvent: {}", event);
        eventStreamPublisher.publish(event);
    }
}
