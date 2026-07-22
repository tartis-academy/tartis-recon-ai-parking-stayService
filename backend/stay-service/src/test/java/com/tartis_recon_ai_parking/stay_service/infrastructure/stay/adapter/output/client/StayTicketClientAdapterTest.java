package com.tartis_recon_ai_parking.stay_service.infrastructure.stay.adapter.output.client;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.client.StayTicketClientAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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

        server.expect(requestTo("http://ticket-service:8080/v1/tickets/entry"))
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
    void shouldIssueExitTicket() {
        // GIVEN
        UUID stayId = UUID.randomUUID();
        UUID entryTicketId = UUID.randomUUID();
        UUID expectedExitTicketId = UUID.randomUUID();

        String jsonResponse = "{\"exitTicketId\": \"" + expectedExitTicketId + "\"}";

        server.expect(requestTo("http://ticket-service:8080/v1/tickets/exit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // WHEN
        UUID exitTicketId = stayTicketClientAdapter.issueExitTicket(stayId, entryTicketId);

        // THEN
        assertEquals(expectedExitTicketId, exitTicketId);
        server.verify();
    }
}