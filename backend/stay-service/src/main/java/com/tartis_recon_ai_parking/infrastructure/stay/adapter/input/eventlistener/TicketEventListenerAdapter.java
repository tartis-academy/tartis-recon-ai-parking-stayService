package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.tartis_recon_ai_parking.application.stay.dto.TicketChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;

// SSE-06: no hay logica de dominio que aplicar. El unico trabajo es reenviar
// por SSE el evento que ya publico ticket-service en RabbitMQ.
@Component
public class TicketEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TicketEventListenerAdapter.class);

    private final StayEventStreamPublisher eventStreamPublisher;

    public TicketEventListenerAdapter(StayEventStreamPublisher eventStreamPublisher) {
        this.eventStreamPublisher = eventStreamPublisher;
    }

    @RabbitListener(queues = RabbitMQConfig.TICKET_CHANGED_QUEUE, containerFactory = "sseListenerContainerFactory")
    public void handleTicketChangedEvent(TicketChangedEvent event) {
        log.info("Recibido TicketChangedEvent {} para el ticket {}", event.eventId(), event.data().ticketId());
        log.debug("TicketChangedEvent completo: {}", event);
        eventStreamPublisher.publish(event);
    }
}
