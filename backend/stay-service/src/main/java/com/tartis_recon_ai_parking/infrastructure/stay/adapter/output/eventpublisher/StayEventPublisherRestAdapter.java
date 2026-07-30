package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventpublisher;

import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventPublisher;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "parking.async.enabled", havingValue = "false")
public class StayEventPublisherRestAdapter implements StayEventPublisher {

    // Se corrige .java por .class
    private static final Logger log = LoggerFactory.getLogger(StayEventPublisherRestAdapter.class);

    private final StayTicketPort ticketPort;
    private final StaySpotPort spotPort;

    public StayEventPublisherRestAdapter(StayTicketPort ticketPort, StaySpotPort spotPort) {
        this.ticketPort = ticketPort;
        this.spotPort = spotPort;
    }

    @Override
    public void publish(StayClosedEvent event) {
        log.info("Modo REST activo: Ejecutando llamadas síncronas de fallback para la estancia {}", event.data().stayId());

        // 1. Llamada REST a ticket-service
        try {
            ticketPort.issueExitTicket(
                event.data().stayId(),
                null,
                event.data().totalAmount()
            );
        } catch (Exception e) {
            log.error("Error al emitir ticket por REST", e);
        }

        // 2. Llamada REST a spot-service
        try {
            if (event.data().spotCode() != null) {
                spotPort.releaseSpot(UUID.fromString(event.data().spotCode()));
            }
        } catch (Exception e) {
            log.error("Error al liberar plaza por REST", e);
        }
    }
}