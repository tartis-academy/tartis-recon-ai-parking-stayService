package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.EntryTicketDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
import com.tartis_recon_ai_parking.application.stay.dto.VehicleAttributes;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckOutUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.GetStayUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.ListStaysUseCase;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleDeactivatedException;
import com.tartis_recon_ai_parking.infrastructure.config.SecurityConfig;
import com.tartis_recon_ai_parking.infrastructure.config.KeycloakRoleConverter;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.CustomizedExceptionAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(StayRestAdapter.class)
@Import({SecurityConfig.class, StayRestMapper.class, CustomizedExceptionAdapter.class})
@ActiveProfiles("test")
class StayRestAdapterMvcTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckInUseCase checkInUseCase;

    @MockitoBean
    private CheckOutUseCase checkOutUseCase;

    @MockitoBean
    private GetStayUseCase getStayUseCase;

    @MockitoBean
    private ListStaysUseCase listStaysUseCase;

    // ==========================================
    // LÓGICA DE NEGOCIO Y EXCEPCIONES
    // ==========================================

    @Test
    @DisplayName("acceso autorizado -> 201 con la estancia creada y el ticket de entrada (CA3)")
    void checkIn_returns201() throws Exception {
        UUID stayId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        StayDTO dto = new StayDTO(
                stayId, UUID.randomUUID(), VehicleType.CAR, spotId, UUID.randomUUID(),
                Instant.parse("2026-07-23T08:30:00Z"), null, null, StayStatus.IN_PROGRESS);
        EntryTicketDTO entryTicket = new EntryTicketDTO(
                ticketId, "BC-0001", Instant.parse("2026-07-23T08:30:00Z"));
        when(checkInUseCase.execute(any())).thenReturn(new CheckInResultDTO(dto, entryTicket));

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.plate").value("1234ABC"))
                .andExpect(jsonPath("$.spotId").value(spotId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.entryTicket.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.entryTicket.barCode").value("BC-0001"));
    }

    @Test
    @DisplayName("los atributos opcionales del totem llegan al caso de uso (STAY-104)")
    void checkIn_forwardsOptionalVehicleAttributes() throws Exception {
        givenCheckInSucceeds();

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\","
                                + "\"brand\":\"Seat\",\"model\":\"Ibiza\",\"color\":\"Rojo\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<StayCreateDTO> captor = ArgumentCaptor.forClass(StayCreateDTO.class);
        verify(checkInUseCase).execute(captor.capture());

        VehicleAttributes attributes = captor.getValue().getVehicleAttributes();
        assertEquals("Seat", attributes.brand());
        assertEquals("Ibiza", attributes.model());
        assertEquals("Rojo", attributes.color());
    }

    @Test
    @DisplayName("check-in sin atributos opcionales -> 201, atributos vacios (retrocompatibilidad)")
    void checkIn_withoutOptionalAttributes_stillWorks() throws Exception {
        givenCheckInSucceeds();

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<StayCreateDTO> captor = ArgumentCaptor.forClass(StayCreateDTO.class);
        verify(checkInUseCase).execute(captor.capture());

        assertTrue(
                captor.getValue().getVehicleAttributes().isEmpty());
    }

    @Test
    @DisplayName("RES-07: check-in devuelve 201 con ticket OFFLINE cuando ticket-service degrada")
    void checkIn_withOfflineTicket_returns201() throws Exception {
        UUID stayId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        String plate = "1234ABC";

        StayDTO stayDto = new StayDTO(
                stayId, UUID.randomUUID(), VehicleType.CAR, spotId, UUID.randomUUID(),
                Instant.parse("2026-07-23T08:30:00Z"), null, null, StayStatus.IN_PROGRESS);

        // DTO de respuesta que genera el caso de uso tras el fallback offline
        EntryTicketDTO offlineTicket = new EntryTicketDTO(
                ticketId, "OFFLINE-ENTRY-" + plate, Instant.parse("2026-07-23T08:30:00Z"));

        when(checkInUseCase.execute(any())).thenReturn(new CheckInResultDTO(stayDto, offlineTicket));

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.plate").value(plate))
                .andExpect(jsonPath("$.spotId").value(spotId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.entryTicket.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.entryTicket.barCode").value("OFFLINE-ENTRY-1234ABC"));
    }

    private void givenCheckInSucceeds() {
        StayDTO dto = new StayDTO(
                UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-23T08:30:00Z"), null, null, StayStatus.IN_PROGRESS);
        EntryTicketDTO entryTicket = new EntryTicketDTO(
                UUID.randomUUID(), "BC-0001", Instant.parse("2026-07-23T08:30:00Z"));
        when(checkInUseCase.execute(any())).thenReturn(new CheckInResultDTO(dto, entryTicket));
    }

    @Test
    @DisplayName("RN-11 vehiculo dado de baja -> 422, barrera cerrada")
    void checkIn_deactivatedVehicle_returns422() throws Exception {
        when(checkInUseCase.execute(any()))
                .thenThrow(new VehicleDeactivatedException("vehiculo de baja"));

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.path").value("/v1/stays/check-in"));
    }

    @Test
    @DisplayName("RN-01 parking completo -> 409")
    void checkIn_noSpot_returns409() throws Exception {
        when(checkInUseCase.execute(any()))
                .thenThrow(new NoAvailableSpotException("parking completo"));

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("CB-05 vehiculo ya dentro -> 409")
    void checkIn_alreadyInside_returns409() throws Exception {
        when(checkInUseCase.execute(any()))
                .thenThrow(new DuplicateActiveStayException("ya dentro"));

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("tipo de vehiculo no reconocido -> 400 (lo detecta el mapper real)")
    void checkIn_invalidType_returns400() throws Exception {
        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"TRUCK\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("check-out correcto -> 200 con importe, minutos y ticket de salida")
    void checkOut_returns200() throws Exception {
        UUID stayId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        StayDTO dto = new StayDTO(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(),
                UUID.randomUUID(), Instant.parse("2026-07-23T08:30:00Z"),
                Instant.parse("2026-07-23T10:00:00Z"), new BigDecimal("3.00"), StayStatus.FINISHED);
        when(checkOutUseCase.execute(any())).thenReturn(new CheckOutResultDTO(dto, ticketId, 90L));

        mockMvc.perform(post("/v1/stays/check-out")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.plate").value("1234ABC"))
                .andExpect(jsonPath("$.totalMinutes").value(90))
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    @DisplayName("check-out sin estancia en curso -> 404")
    void checkOut_notFound_returns404() throws Exception {
        when(checkOutUseCase.execute(any()))
                .thenThrow(new StayNotFoundException("sin estancia en curso"));

        mockMvc.perform(post("/v1/stays/check-out")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /v1/stays/{id} -> 200 con el detalle de la estancia (HU-08)")
    void getStay_returns200() throws Exception {
        UUID stayId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        when(getStayUseCase.execute(stayId)).thenReturn(new StayDTO(
                stayId, UUID.randomUUID(), VehicleType.CAR, spotId, UUID.randomUUID(),
                Instant.parse("2026-07-23T08:30:00Z"), null, null, StayStatus.IN_PROGRESS));

        mockMvc.perform(get("/v1/stays/{stayId}", stayId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.spotId").value(spotId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("GET /v1/stays/{id} -> 404 si no existe la estancia")
    void getStay_notFound_returns404() throws Exception {
        UUID stayId = UUID.randomUUID();
        when(getStayUseCase.execute(stayId)).thenThrow(StayNotFoundException.withId(stayId));

        mockMvc.perform(get("/v1/stays/{stayId}", stayId)
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /v1/stays -> 200 con la pagina de estancias filtrada por status (HU-08)")
    void listStays_returns200() throws Exception {
        UUID stayId = UUID.randomUUID();
        StayDTO dto = new StayDTO(
                stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-23T08:30:00Z"), null, null, StayStatus.IN_PROGRESS);
        when(listStaysUseCase.execute(StayStatus.IN_PROGRESS, 0, 20))
                .thenReturn(new StayPageDTO(List.of(dto), 0, 20, 1L, 1));

        mockMvc.perform(get("/v1/stays")
                        .param("status", "IN_PROGRESS")
                        .param("page", "0")
                        .param("size", "20")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /v1/stays sin params -> 200 usando page=0 size=20 y sin filtro de status")
    void listStays_defaults_returns200() throws Exception {
        when(listStaysUseCase.execute(null, 0, 20))
                .thenReturn(new StayPageDTO(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/v1/stays")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // ==========================================
    // ERRORS DE ENTRADA (400/405, escenarios de ruptura BD)
    // ==========================================

    @Test
    @DisplayName("check-out sin matricula -> 400 por validacion @NotBlank (antes dependia del caso de uso)")
    void checkOut_emptyPlate_returns400() throws Exception {
        mockMvc.perform(post("/v1/stays/check-out")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(checkOutUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("check-in con matricula vacia -> 400 por validacion")
    void checkIn_emptyPlate_returns400() throws Exception {
        mockMvc.perform(post("/v1/stays/check-in")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(checkInUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("GET /v1/stays/{id} con id que no es UUID -> 400 (se escapo como 500)")
    void getStay_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/v1/stays/no-es-un-uuid")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /v1/stays?status=INVENTADO -> 400 por enum invalido")
    void listStays_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/v1/stays")
                        .param("status", "INVENTADO")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /v1/stays?page=-1 -> 400 por paginacion invalida (lo valida el adaptador antes de la persistencia)")
    void listStays_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/v1/stays")
                        .param("page", "-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Parametros de paginacion invalidos"));
        verify(listStaysUseCase, never()).execute(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /v1/stays?size=0 -> 400 por paginacion invalida")
    void listStays_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/v1/stays")
                        .param("size", "0")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Parametros de paginacion invalidos"));
        verify(listStaysUseCase, never()).execute(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /v1/stays?size=1000000 -> se recorta al tope (100) sin disparar una consulta enorme")
    void listStays_hugeSize_capsAtMax() throws Exception {
        when(listStaysUseCase.execute(null, 0, 100))
                .thenReturn(new StayPageDTO(List.of(), 0, 100, 0L, 0));

        mockMvc.perform(get("/v1/stays")
                        .param("page", "0")
                        .param("size", "1000000")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("PUT sobre /v1/stays (solo GET) -> 405")
    void putOnGetOnlyEndpoint_returns405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/stays")
                        .with(adminJwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // ==========================================
    // PRUEBAS DE AUTORIZACIÓN POR ROL (SEC-10)
    // ==========================================
    @Test
    @DisplayName("Debe rechazar con 401 una peticion sin token (y validar WWW-Authenticate + ErrorResponse)")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/v1/stays"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/v1/stays"));
    }

    @Test
    @DisplayName("OPERARIO: Debe permitir check-in (201)")
    void shouldAllowCheckInForOperario() throws Exception {
        UUID stayId = UUID.randomUUID();
        StayDTO dto = new StayDTO(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS);
        EntryTicketDTO ticket = new EntryTicketDTO(UUID.randomUUID(), "BC-001", Instant.now());
        when(checkInUseCase.execute(any())).thenReturn(new CheckInResultDTO(dto, ticket));

        mockMvc.perform(post("/v1/stays/check-in")
                        .with(operarioJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("USER: Debe denegar check-in (403)")
    void shouldDenyCheckInForUser() throws Exception {
        mockMvc.perform(post("/v1/stays/check-in")
                        .with(userJwt("1234ABC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isForbidden());
        verify(checkInUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("OPERARIO: Debe permitir check-out (200)")
    void shouldAllowCheckOutForOperario() throws Exception {
        StayDTO dto = new StayDTO(UUID.randomUUID(), UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(), new BigDecimal("3.00"), StayStatus.FINISHED);
        when(checkOutUseCase.execute(any())).thenReturn(new CheckOutResultDTO(dto, UUID.randomUUID(), 60L));

        mockMvc.perform(post("/v1/stays/check-out")
                        .with(operarioJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER: Debe denegar check-out (403)")
    void shouldDenyCheckOutForUser() throws Exception {
        mockMvc.perform(post("/v1/stays/check-out")
                        .with(userJwt("1234ABC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isForbidden());
        verify(checkOutUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("USER: Debe permitir consultar una estancia por ID (200) si es su vehiculo")
    void shouldAllowGetStayForUser() throws Exception {
        UUID stayId = UUID.randomUUID();
        String plate = "1234ABC";
        when(getStayUseCase.execute(stayId)).thenReturn(new StayDTO(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS, plate));

        mockMvc.perform(get("/v1/stays/{stayId}", stayId)
                        .with(userJwt(plate)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER: Debe denegar consultar una estancia por ID (403) si no es su vehiculo (IDOR)")
    void shouldDenyGetStayForUserWhenNotOwner() throws Exception {
        UUID stayId = UUID.randomUUID();
        String stayPlate = "1234ABC";
        String userPlate = "5678XYZ";
        when(getStayUseCase.execute(stayId)).thenReturn(new StayDTO(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS, stayPlate));

        mockMvc.perform(get("/v1/stays/{stayId}", stayId)
                        .with(userJwt(userPlate)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER: Debe denegar listar todas las estancias (403)")
    void shouldDenyListStaysForUser() throws Exception {
        mockMvc.perform(get("/v1/stays")
                        .with(userJwt("1234ABC")))
                .andExpect(status().isForbidden());
        verify(listStaysUseCase, never()).execute(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("OPERARIO: Debe permitir consultar una estancia por ID (200)")
    void shouldAllowGetStayForOperario() throws Exception {
        UUID stayId = UUID.randomUUID();
        when(getStayUseCase.execute(stayId)).thenReturn(new StayDTO(stayId, UUID.randomUUID(), VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS));

        mockMvc.perform(get("/v1/stays/{stayId}", stayId)
                        .with(operarioJwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OPERARIO: Debe permitir listar todas las estancias (200)")
    void shouldAllowListStaysForOperario() throws Exception {
        when(listStaysUseCase.execute(null, 0, 20))
                .thenReturn(new StayPageDTO(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/v1/stays")
                        .with(operarioJwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("UNAUTHENTICATED: Debe rechazar check-in sin token (401)")
    void checkIn_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/v1/stays/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/v1/stays/check-in"));

        verify(checkInUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("UNAUTHENTICATED: Debe rechazar check-out sin token (401)")
    void checkOut_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/v1/stays/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/v1/stays/check-out"));

        verify(checkOutUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("UNAUTHENTICATED: Debe rechazar consultar estancia por ID sin token (401)")
    void getStayById_withoutToken_returns401() throws Exception {
        UUID stayId = UUID.randomUUID();
        mockMvc.perform(get("/v1/stays/{stayId}", stayId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/v1/stays/" + stayId));

        verify(getStayUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("UNAUTHENTICATED: Debe rechazar listar estancias sin token (401)")
    void listStays_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/stays"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verify(listStaysUseCase, never()).execute(any(), anyInt(), anyInt());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                )
                .authorities(new KeycloakRoleConverter());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operarioJwt() {
        return jwt()
                .jwt(j -> j
                        .claim("sub", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("OPERARIO")))
                )
                .authorities(new KeycloakRoleConverter());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(String plate) {
        return jwt()
                .jwt(j -> {
                    j.claim("sub", UUID.randomUUID().toString());
                    j.claim("realm_access", Map.of("roles", List.of("USER")));
                    if (plate != null) {
                        j.claim("plate", plate);
                    }
                })
                .authorities(new KeycloakRoleConverter());
    }
}
