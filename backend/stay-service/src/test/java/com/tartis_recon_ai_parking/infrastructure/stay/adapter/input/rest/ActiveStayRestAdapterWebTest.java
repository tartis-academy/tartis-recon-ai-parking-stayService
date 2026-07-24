package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.GetActiveStayUseCase;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.CustomizedExceptionAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActiveStayRestAdapterWebTest {

    private GetActiveStayUseCase getActiveStayUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        getActiveStayUseCase = Mockito.mock(GetActiveStayUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActiveStayRestAdapter(getActiveStayUseCase, new StayRestMapper()))
                .setControllerAdvice(new CustomizedExceptionAdapter())
                .build();
    }

    @Test
    @DisplayName("estancia activa encontrada -> 200 OK con StayResponse")
    void getActiveStay_returns200() throws Exception {
        UUID stayId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        Instant checkIn = Instant.parse("2026-07-23T08:30:00Z");

        StayDTO stayDTO = new StayDTO(
                stayId, vehicleId, VehicleType.CAR, spotId, tariffId,
                checkIn, null, null, StayStatus.IN_PROGRESS);

        when(getActiveStayUseCase.execute(eq(vehicleId))).thenReturn(stayDTO);

        mockMvc.perform(get("/v1/activeStay/" + vehicleId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.spotId").value(spotId.toString()))
                .andExpect(jsonPath("$.tariffId").value(tariffId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.checkIn").exists());
    }

    @Test
    @DisplayName("estancia activa no encontrada -> 404 Not Found")
    void getActiveStay_returns404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(getActiveStayUseCase.execute(eq(id)))
                .thenThrow(StayNotFoundException.activeByVehicleId(id));

        mockMvc.perform(get("/v1/activeStay/" + id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No existe ninguna estancia en curso para el vehiculo " + id));
    }
}
