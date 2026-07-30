package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.EntryTicketDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        when(listStaysUseCase.execute(eq(StayStatus.IN_PROGRESS), eq(0), eq(20)))
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
        when(listStaysUseCase.execute(eq(null), eq(0), eq(20)))
                .thenReturn(new StayPageDTO(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/v1/stays")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // ==========================================
    // PRUEBAS DE AUTORIZACIÓN POR ROL (SEC-10)
    // ==========================================

    @Test
    @DisplayName("Debe rechazar con 401 una peticion sin token")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/v1/stays"))
                .andExpect(status().isUnauthorized());
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
        when(listStaysUseCase.execute(eq(null), eq(0), eq(20)))
                .thenReturn(new StayPageDTO(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/v1/stays")
                        .with(operarioJwt()))
                .andExpect(status().isOk());
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
