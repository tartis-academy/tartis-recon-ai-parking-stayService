package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventpublisher;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventPublisher;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StayEventPublisherAdapter implements StayEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public StayEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(StayClosedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_STAY_CLOSED,
                event);
    }
}