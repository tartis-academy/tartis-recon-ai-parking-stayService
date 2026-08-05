package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import com.tartis_recon_ai_parking.application.stay.dto.VehicleChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// SSE-06: no hay logica de dominio que aplicar. El unico trabajo es reenviar
// por SSE el evento que ya publico vehicle-service en RabbitMQ.
@Component
public class VehicleEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(VehicleEventListenerAdapter.class);

    private final StayEventStreamPublisher eventStreamPublisher;

    public VehicleEventListenerAdapter(StayEventStreamPublisher eventStreamPublisher) {
        this.eventStreamPublisher = eventStreamPublisher;
    }

    @RabbitListener(queues = RabbitMQConfig.VEHICLE_CHANGED_QUEUE, containerFactory = "sseListenerContainerFactory")
    public void handleVehicleChangedEvent(VehicleChangedEvent event) {
        log.info("Recibido VehicleChangedEvent {} para el vehiculo {}", event.eventId(), event.data().vehicleId());
        log.debug("VehicleChangedEvent completo: {}", event);
        eventStreamPublisher.publish(event);
    }
}
