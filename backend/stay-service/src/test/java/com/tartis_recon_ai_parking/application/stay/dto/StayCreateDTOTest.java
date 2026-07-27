package com.tartis_recon_ai_parking.application.stay.dto;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StayCreateDTOTest {

    @Test
    @DisplayName("Debe exponer por los getters los valores dados en el constructor")
    void shouldExposeConstructorValues() {
        StayCreateDTO dto = new StayCreateDTO("1234ABC", VehicleType.CAR);

        assertThat(dto.getPlate()).isEqualTo("1234ABC");
        assertThat(dto.getVehicleType()).isEqualTo(VehicleType.CAR);
    }

    @Test
    @DisplayName("Debe admitir vehicleType nulo (auto-registro no requerido)")
    void shouldAllowNullVehicleType() {
        StayCreateDTO dto = new StayCreateDTO("1234ABC", null);

        assertThat(dto.getPlate()).isEqualTo("1234ABC");
        assertThat(dto.getVehicleType()).isNull();
    }
}
