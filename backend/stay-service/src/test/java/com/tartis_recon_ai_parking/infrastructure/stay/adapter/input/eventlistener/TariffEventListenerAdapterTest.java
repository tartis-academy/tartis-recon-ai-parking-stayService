package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.eventlistener;

import com.tartis_recon_ai_parking.application.stay.dto.TariffChangedEvent;
import com.tartis_recon_ai_parking.application.stay.port.output.StayEventStreamPublisher;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TariffEventListenerAdapterTest {

    @Mock
    private StayEventStreamPublisher eventStreamPublisher;

    @InjectMocks
    private TariffEventListenerAdapter adapter;

    @Test
    @DisplayName("Debe reenviar el TariffChangedEvent recibido de RabbitMQ al puerto SSE tal cual")
    void handleTariffChangedEvent_ForwardsEventToStreamPublisher() {
        TariffChangedEvent event = new TariffChangedEvent(
                UUID.randomUUID(), "TariffChangedEvent", "v1", Instant.now(),
                new TariffChangedEvent.TariffChangedData(
                        UUID.randomUUID(), "Standard", VehicleType.CAR,
                        new BigDecimal("0.05"), new BigDecimal("2.0"), true));

        adapter.handleTariffChangedEvent(event);

        verify(eventStreamPublisher).publish(event);
    }
}
