package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tartis_recon_ai_parking.application.stay.dto.TicketChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;

@ExtendWith(MockitoExtension.class)
class TicketEventListenerAdapterTest {

    @Mock
    private StayEventStreamPublisher eventStreamPublisher;

    @InjectMocks
    private TicketEventListenerAdapter listener;

    @Test
    void handleTicketChangedEvent_shouldForwardToEventStreamPublisher() {
        TicketChangedEvent event = new TicketChangedEvent(
            UUID.randomUUID(),
            "TicketChangedEvent",
            "v1",
            Instant.now(),
            new TicketChangedEvent.TicketChangedData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TCK-123456",
                Instant.now(),
                "CREATED",
                BigDecimal.ZERO
            )
        );

        listener.handleTicketChangedEvent(event);

        verify(eventStreamPublisher).publish(event);
    }
}
