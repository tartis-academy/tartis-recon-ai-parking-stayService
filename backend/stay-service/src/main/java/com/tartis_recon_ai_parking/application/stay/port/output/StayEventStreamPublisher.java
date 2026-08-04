package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.application.stay.dto.SpotStatusChangedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.StayClosedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreatedEvent;
import com.tartis_recon_ai_parking.application.stay.dto.TariffChangedEvent;

// Reenvia por SSE un evento ya publicado en RabbitMQ o generado en el flujo de estancia.
public interface StayEventStreamPublisher {

    void publish(StayClosedEvent event);

    void publish(StayCreatedEvent event);

    void publish(TariffChangedEvent event);

    void publish(SpotStatusChangedEvent event);
}

