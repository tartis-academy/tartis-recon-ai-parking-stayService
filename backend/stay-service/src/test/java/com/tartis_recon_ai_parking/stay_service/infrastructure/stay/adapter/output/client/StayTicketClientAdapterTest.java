package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayTicketClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StayTicketClientAdapterTest {

    private StayTicketClientAdapter stayTicketClientAdapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        stayTicketClientAdapter = new StayTicketClientAdapter(builder, "http://ticket-service:8080");
    }

    @Test
    void shouldIssueEntryTicket() {
        // GIVEN
        UUID stayId = UUID.randomUUID();
        UUID expectedTicketId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-30T10:00:00Z");

        String jsonResponse = """
                {
                    "ticketId": "%s",
                    "barCode": "BC-987654321",
                    "issuedAt": "2026-03-30T10:00:00Z"
                }
                """.formatted(expectedTicketId);

        server.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        StayTicketPort.EntryTicketInfo ticketInfo = stayTicketClientAdapter.issueEntryTicket(stayId, "1234ABC", now);

        // THEN
        assertNotNull(ticketInfo);
        assertEquals(expectedTicketId, ticketInfo.ticketId());
        assertEquals("BC-987654321", ticketInfo.barCode());
        assertEquals(now, ticketInfo.issuedAt());
        server.verify();
    }

    @Test
    void shouldThrowTicketServiceException_whenIssueEntryTicketUnreachable() {
        // GIVEN: ticket-service caido / devuelve 500 (fallo de infraestructura, no de negocio)
        server.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // WHEN & THEN: no debe colarse la RestClientException cruda
        TicketServiceException ex = assertThrows(TicketServiceException.class, () ->
            stayTicketClientAdapter.issueEntryTicket(UUID.randomUUID(), "1234ABC", Instant.now())
        );
        assertThat(ex.getCause()).isNotNull();
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
        assertThrows(TicketServiceException.class, () ->
            stayTicketClientAdapter.issueEntryTicket(UUID.randomUUID(), "1234ABC", Instant.now())
        );
        server.verify();
    }

    @Test
    void shouldIssueExitTicket() {
        // GIVEN: TicketRequest de ticket-service (POST /v1/tickets) solo acepta
        // stayId, y TicketResponse identifica el ticket como "uniqueId".
        UUID stayId = UUID.randomUUID();
        UUID entryTicketId = UUID.randomUUID();
        UUID expectedExitTicketId = UUID.randomUUID();

        String jsonResponse = "{\"uniqueId\": \"" + expectedExitTicketId + "\"}";

        server.expect(requestTo("http://ticket-service:8080/v1/tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        UUID exitTicketId = stayTicketClientAdapter.issueExitTicket(stayId, entryTicketId, BigDecimal.TEN);

        // THEN
        assertEquals(expectedExitTicketId, exitTicketId);
        server.verify();
    }

    @Test
    void shouldThrowExceptionWhenTicketServiceReturnsNoUniqueId() {
        // GIVEN: respuesta sin uniqueId
        UUID stayId = UUID.randomUUID();
        UUID entryTicketId = UUID.randomUUID();

        server.expect(requestTo("http://ticket-service:8080/v1/tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // WHEN & THEN
        assertThrows(TicketServiceException.class, () ->
            stayTicketClientAdapter.issueExitTicket(stayId, entryTicketId, BigDecimal.TEN)
        );

        server.verify();
    }

    @Test
    void shouldThrowTicketServiceException_whenIssueExitTicketUnreachable() {
        // GIVEN: ticket-service caido / devuelve 500 (fallo de infraestructura, no de negocio)
        server.expect(requestTo("http://ticket-service:8080/v1/tickets"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // WHEN & THEN: no debe colarse la RestClientException cruda
        TicketServiceException ex = assertThrows(TicketServiceException.class, () ->
            stayTicketClientAdapter.issueExitTicket(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN)
        );
        assertThat(ex.getCause()).isNotNull();
        server.verify();
    }
}