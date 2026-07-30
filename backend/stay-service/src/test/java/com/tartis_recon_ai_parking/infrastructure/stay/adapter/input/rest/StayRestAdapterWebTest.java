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
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.CustomizedExceptionAdapter;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Borde HTTP del check-in ({@code POST /v1/stays/check-in}), con MockMvc standalone.
 *
 * <p>Demuestra que cada denegacion sale con su codigo (la barrera solo se abre con
 * 201) y que el {@link CustomizedExceptionAdapter} traduce cada excepcion de dominio
 * (IN-36). Usa el {@link StayRestMapper} real, asi que tambien cubre el parseo del
 * tipo invalido por el propio camino HTTP.
 */
class StayRestAdapterWebTest {

    private CheckInUseCase checkInUseCase;
    private CheckOutUseCase checkOutUseCase;
    private GetStayUseCase getStayUseCase;
    private ListStaysUseCase listStaysUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkInUseCase = Mockito.mock(CheckInUseCase.class);
        checkOutUseCase = Mockito.mock(CheckOutUseCase.class);
        getStayUseCase = Mockito.mock(GetStayUseCase.class);
        listStaysUseCase = Mockito.mock(ListStaysUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StayRestAdapter(checkInUseCase, checkOutUseCase, getStayUseCase,
                        listStaysUseCase, new StayRestMapper()))
                .setControllerAdvice(new CustomizedExceptionAdapter())
                .build();
    }

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("tipo de vehiculo no reconocido -> 400 (lo detecta el mapper real)")
    void checkIn_invalidType_returns400() throws Exception {
        mockMvc.perform(post("/v1/stays/check-in")
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

        mockMvc.perform(get("/v1/stays/{stayId}", stayId))
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

        mockMvc.perform(get("/v1/stays/{stayId}", stayId))
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
                        .param("size", "20"))
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

        mockMvc.perform(get("/v1/stays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }
}
