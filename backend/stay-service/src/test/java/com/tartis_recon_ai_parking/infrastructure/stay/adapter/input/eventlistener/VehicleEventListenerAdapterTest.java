package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import com.tartis_recon_ai_parking.application.stay.dto.VehicleChangedEvent;
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
class VehicleEventListenerAdapterTest {

    @Mock
    private StayEventStreamPublisher eventStreamPublisher;

    @InjectMocks
    private VehicleEventListenerAdapter adapter;

    @Test
    @DisplayName("Debe reenviar el VehicleChangedEvent recibido de RabbitMQ al puerto SSE tal cual")
    void handleVehicleChangedEvent_ForwardsEventToStreamPublisher() {
        VehicleChangedEvent event = new VehicleChangedEvent(
                UUID.randomUUID(), "VehicleChangedEvent", "v1", Instant.now(),
                new VehicleChangedEvent.VehicleChangedData(
                        UUID.randomUUID(), "1234BCD", VehicleType.CAR,
                        "Toyota", "Corolla", "Red", 4, false, true));

        adapter.handleVehicleChangedEvent(event);

        verify(eventStreamPublisher).publish(event);
    }
}
