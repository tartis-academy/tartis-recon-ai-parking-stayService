package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventpublisher;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
// Se activa SOLO si parking.async.enabled es true (o si la propiedad no está definida)
@ConditionalOnProperty(name = "parking.async.enabled", havingValue = "true", matchIfMissing = true)
public class StayEventPublisherAdapter implements StayEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE_NAME = "parking-events-exchange";
    private static final String ROUTING_KEY = "stay-closed-v1";

    public StayEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(StayClosedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, event);
    }
}