package com.tartis_recon_ai_parking.domain.stay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

// Pruebas unitarias del enum StayStatus. Verifican que la clasificacion de estados
// terminales (IN-19) es correcta, ya que de ella depende que Stay permita o no
// nuevas transiciones.
class StayStatusTest {

    @Test
    @DisplayName("IN_PROGRESS no debe ser un estado terminal")
    void inProgressIsNotTerminal() {
        // QUE HACE / QUE DEBERIA HACER:
        // IN_PROGRESS es el unico estado no terminal: una estancia en curso admite cambios.
        assertThat(StayStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StayStatus.class, names = { "FINISHED", "CANCELLED" })
    @DisplayName("FINISHED y CANCELLED deben ser estados terminales (IN-19)")
    void finishedAndCancelledAreTerminal(StayStatus status) {
        // QUE HACE / QUE DEBERIA HACER:
        // Ambos estados finales son terminales, por lo que la estancia deja de admitir cambios.
        assertThat(status.isTerminal()).isTrue();
    }
}
