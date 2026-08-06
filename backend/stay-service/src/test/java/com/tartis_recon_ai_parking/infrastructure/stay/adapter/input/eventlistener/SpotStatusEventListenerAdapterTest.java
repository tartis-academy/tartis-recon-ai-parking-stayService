package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import com.tartis_recon_ai_parking.application.stay.dto.SpotStatusChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpotStatusEventListenerAdapterTest {

    @Mock
    private StayEventStreamPublisher eventStreamPublisher;

    @InjectMocks
    private SpotStatusEventListenerAdapter adapter;

    @Test
    @DisplayName("Debe reenviar el SpotStatusChangedEvent recibido de RabbitMQ al puerto SSE tal cual")
    void handleSpotStatusChangedEvent_ForwardsEventToStreamPublisher() {
        SpotStatusChangedEvent event = new SpotStatusChangedEvent(
                UUID.randomUUID(), "SpotStatusChangedEvent", "v1", Instant.now(),
                new SpotStatusChangedEvent.SpotStatusChangedData(
                        UUID.randomUUID(), VehicleType.CAR, "AVAILABLE"));

        adapter.handleSpotStatusChangedEvent(event);

        verify(eventStreamPublisher).publish(event);
    }
}
