package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;
import com.tartis_recon_ai_parking.infrastructure.config.RabbitMQConfig;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayTicketClientAdapter;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.eventpublisher.event.EntryTicketOfflineEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(MockitoExtension.class)
class StayTicketClientAdapterTest {

        @Mock
        private RabbitTemplate rabbitTemplate;

        private StayTicketClientAdapter stayTicketClientAdapter;
        private MockRestServiceServer server;

        @BeforeEach
        void setUp() {
                RestClient.Builder builder = RestClient.builder();
                server = MockRestServiceServer.bindTo(builder).build();
                stayTicketClientAdapter = new StayTicketClientAdapter(builder, rabbitTemplate,
                                "http://ticket-service:8080");
        }

        @Test
        void shouldIssueEntryTicket() {
                // GIVEN
                UUID stayId = UUID.randomUUID();
                UUID expectedTicketId = UUID.randomUUID();
                Instant now = Instant.parse("2026-03-30T10:00:00Z");

                // Contrato real de ticket-service (EntryTicketResponse): id/code, no
                // ticketId/barCode. Antes del fix, este fixture con los nombres que
                // StayTicketPort.EntryTicketInfo esperaba ocultaba que en produccion
                // Jackson dejaba ticketId/barCode a null (los nombres no casaban).
                String jsonResponse = """
                                {
                                    "id": "%s",
                                    "stayId": "%s",
                                    "issuedAt": "2026-03-30T10:00:00Z",
                                    "code": "BC-987654321"
                                }
                                """.formatted(expectedTicketId, stayId);

                server.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

                // WHEN
                StayTicketPort.EntryTicketInfo ticketInfo = stayTicketClientAdapter.issueEntryTicket(stayId, "1234ABC",
                                now);

                // THEN
                assertNotNull(ticketInfo);
                assertEquals(expectedTicketId, ticketInfo.ticketId());
                assertEquals("BC-987654321", ticketInfo.barCode());
                assertEquals(now, ticketInfo.issuedAt());
                server.verify();
        }

        @Test
        void shouldThrowTicketServiceException_whenEntryTicketResponseEmpty() {
                // GIVEN: respuesta 204 sin cuerpo (contrato incumplido por ticket-service)
                server.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withNoContent());

                // WHEN & THEN: sin esto, CheckInUseCase explotaria con NullPointerException
                // cruda al leer ticket.ticketId().
                assertThrows(TicketServiceException.class,
                                () -> stayTicketClientAdapter.issueEntryTicket(UUID.randomUUID(), "1234ABC",
                                                Instant.now()));
                server.verify();
        }

        @Test
        @DisplayName("B2: Error 4xx no degrada y relanza excepción envuelta en TicketServiceException")
        void issueEntryTicket_whenClientError4xx_shouldNotDegradeToOfflineAndThrowException() {
                server.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withStatus(HttpStatus.CONFLICT));

                UUID stayId = UUID.randomUUID();
                String plate = "1234ABC";
                Instant now = Instant.now();

                TicketServiceException ex = assertThrows(TicketServiceException.class,
                                () -> stayTicketClientAdapter.issueEntryTicket(stayId, plate, now));

                assertThat(ex.getCause()).isInstanceOf(HttpClientErrorException.Conflict.class);
                verifyNoInteractions(rabbitTemplate);
                server.verify();
        }

        @Test
        @DisplayName("RES-07: El fallback degrada a ticket offline y publica evento en RabbitMQ")
        void issueEntryTicketFallback_shouldDegradeToOfflineAndPublishRabbitEvent() throws Throwable {
                // GIVEN
                UUID stayId = UUID.randomUUID();
                String plate = "1234ABC";
                Instant now = Instant.now();
                Throwable cause = new RuntimeException("500 Internal Server Error");

                // WHEN (Invocación directa del fallback en test unitario sin AOP)
                StayTicketPort.EntryTicketInfo result = stayTicketClientAdapter.issueEntryTicketFallback(stayId, plate,
                                now, cause);

                // THEN
                assertThat(result).isNotNull();
                assertThat(result.barCode()).startsWith("OFFLINE-ENTRY-");

                verify(rabbitTemplate).convertAndSend(
                                eq(RabbitMQConfig.EXCHANGE_NAME),
                                eq(RabbitMQConfig.ROUTING_KEY_ENTRY_TICKET_OFFLINE),
                                any(EntryTicketOfflineEvent.class));
        }
}