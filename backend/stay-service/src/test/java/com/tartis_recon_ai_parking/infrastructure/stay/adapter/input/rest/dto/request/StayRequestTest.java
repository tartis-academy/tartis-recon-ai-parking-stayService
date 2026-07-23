package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StayRequestTest {

    @Test
    @DisplayName("Debe almacenar y devolver los campos plate y vehicleType")
    void shouldHoldFields() {
        StayRequest request = new StayRequest();
        request.plate = "1234ABC";
        request.vehicleType = "CAR";

        assertThat(request.plate).isEqualTo("1234ABC");
        assertThat(request.vehicleType).isEqualTo("CAR");
    }
}
