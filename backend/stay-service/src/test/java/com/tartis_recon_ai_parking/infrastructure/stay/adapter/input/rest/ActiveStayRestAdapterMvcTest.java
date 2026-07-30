package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.GetActiveStayUseCase;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;
import com.tartis_recon_ai_parking.infrastructure.config.SecurityConfig;
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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActiveStayRestAdapter.class)
@Import({SecurityConfig.class, StayRestMapper.class, CustomizedExceptionAdapter.class})
class ActiveStayRestAdapterMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetActiveStayUseCase getActiveStayUseCase;

    // ==========================================
    // LÓGICA DE NEGOCIO Y EXCEPCIONES (CON ROL ADMIN)
    // ==========================================

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
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No existe ninguna estancia en curso para el vehiculo " + id));
    }

    // ==========================================
    // PRUEBAS DE AUTORIZACIÓN POR ROL (SEC-10)
    // ==========================================

    @Test
    @DisplayName("OPERARIO: Debe permitir consultar estancia activa (200)")
    void shouldAllowGetActiveStayForOperario() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        StayDTO stayDTO = new StayDTO(UUID.randomUUID(), vehicleId, VehicleType.CAR, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, StayStatus.IN_PROGRESS);
        when(getActiveStayUseCase.execute(eq(vehicleId))).thenReturn(stayDTO);

        mockMvc.perform(get("/v1/activeStay/" + vehicleId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERARIO")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER: Debe denegar consultar estancia activa (403)")
    void shouldDenyGetActiveStayForUser() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        mockMvc.perform(get("/v1/activeStay/" + vehicleId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        verify(getActiveStayUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("Debe rechazar con 401 una peticion sin token")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/v1/activeStay/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
