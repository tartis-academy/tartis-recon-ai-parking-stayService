package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import com.tartis_recon_ai_parking.application.stay.dto.TariffChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// SSE-06: no hay logica de dominio que aplicar. El unico trabajo es reenviar
// por SSE el evento que ya publico tariff-service en RabbitMQ.
@Component
public class TariffEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TariffEventListenerAdapter.class);

    private final StayEventStreamPublisher eventStreamPublisher;

    public TariffEventListenerAdapter(StayEventStreamPublisher eventStreamPublisher) {
        this.eventStreamPublisher = eventStreamPublisher;
    }

    @RabbitListener(queues = RabbitMQConfig.TARIFF_CHANGED_QUEUE)
    public void handleTariffChangedEvent(TariffChangedEvent event) {
        log.info("Recibido TariffChangedEvent: {}", event);
        eventStreamPublisher.publish(event);
    }
}
