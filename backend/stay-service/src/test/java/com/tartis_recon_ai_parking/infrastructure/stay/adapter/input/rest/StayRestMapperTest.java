package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.StayCreateDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckInResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Traduccion HTTP <-> aplicacion del check-in. Cubre especialmente el parseo del
 * tipo de vehiculo, que llega como {@code String} opcional y puede venir invalido.
 */
class StayRestMapperTest {

    private final StayRestMapper mapper = new StayRestMapper();

    @Test
    @DisplayName("tipo valido se convierte al enum del dominio")
    void toCreateDTO_parsesValidType() {
        StayCreateDTO dto = mapper.toCreateDTO(request("1234ABC", "CAR_PMR"));

        assertEquals("1234ABC", dto.getPlate());
        assertEquals(VehicleType.CAR_PMR, dto.getVehicleType());
    }

    @Test
    @DisplayName("tipo en minusculas se normaliza a mayusculas")
    void toCreateDTO_isCaseInsensitive() {
        assertEquals(VehicleType.MOTORBIKE, mapper.toCreateDTO(request("1234ABC", "motorbike")).getVehicleType());
    }

    @Test
    @DisplayName("tipo ausente (null) queda null: se resolvera desde vehicle-service")
    void toCreateDTO_nullTypeStaysNull() {
        assertNull(mapper.toCreateDTO(request("1234ABC", null)).getVehicleType());
    }

    @Test
    @DisplayName("tipo en blanco queda null")
    void toCreateDTO_blankTypeStaysNull() {
        assertNull(mapper.toCreateDTO(request("1234ABC", "   ")).getVehicleType());
    }

    @Test
    @DisplayName("tipo presente pero no reconocido: peticion invalida (no fallo interno)")
    void toCreateDTO_invalidTypeThrows() {
        InvalidStayException ex = assertThrows(InvalidStayException.class,
                () -> mapper.toCreateDTO(request("1234ABC", "TRUCK")));
        assertEquals("Tipo de vehiculo no reconocido: TRUCK", ex.getMessage());
    }

    @Test
    @DisplayName("respuesta: mapea los campos y deja entryTicket a null (sin ticket-service)")
    void toCheckInResponse_mapsFields() {
        UUID stayId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        Instant checkIn = Instant.parse("2026-07-23T08:30:00Z");
        StayDTO dto = new StayDTO(stayId, UUID.randomUUID(), VehicleType.CAR, spotId,
                UUID.randomUUID(), checkIn, null, null, StayStatus.IN_PROGRESS);

        CheckInResponse response = mapper.toCheckInResponse(dto, "1234ABC");

        assertEquals(stayId, response.getStayId());
        assertEquals("1234ABC", response.getPlate());
        assertEquals(spotId, response.getSpotId());
        assertEquals(checkIn, response.getCheckIn());
        assertEquals(StayStatus.IN_PROGRESS, response.getStatus());
        assertNull(response.getEntryTicket());
    }

    private static StayRequest request(String plate, String vehicleType) {
        StayRequest r = new StayRequest();
        r.plate = plate;
        r.vehicleType = vehicleType;
        return r;
    }
}
