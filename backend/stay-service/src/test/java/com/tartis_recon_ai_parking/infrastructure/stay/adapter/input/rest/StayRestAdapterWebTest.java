package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckOutUseCase;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkInUseCase = Mockito.mock(CheckInUseCase.class);
        checkOutUseCase = Mockito.mock(CheckOutUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StayRestAdapter(checkInUseCase, checkOutUseCase, new StayRestMapper()))
                .setControllerAdvice(new CustomizedExceptionAdapter())
                .build();
    }

    @Test
    @DisplayName("acceso autorizado -> 201 con la estancia creada (CA3)")
    void checkIn_returns201() throws Exception {
        UUID stayId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        when(checkInUseCase.execute(any())).thenReturn(new StayDTO(
                stayId, UUID.randomUUID(), VehicleType.CAR, spotId, UUID.randomUUID(),
                Instant.parse("2026-07-23T08:30:00Z"), null, null, StayStatus.IN_PROGRESS));

        mockMvc.perform(post("/v1/stays/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"1234ABC\",\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.plate").value("1234ABC"))
                .andExpect(jsonPath("$.spotId").value(spotId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
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
                .andExpect(jsonPath("$.amount").value(3.00))
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
}
