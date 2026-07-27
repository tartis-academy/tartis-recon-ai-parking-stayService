package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StayPageResponseTest {

    @Test
    @DisplayName("El constructor completo debe rellenar todos los campos")
    void allArgsConstructor() {
        List<StayResponse> content = List.of(new StayResponse());
        StayPageResponse page = new StayPageResponse(content, 0, 20, 1L, 1);

        assertThat(page.getContent()).isEqualTo(content);
        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("El constructor vacio y los setters deben funcionar")
    void noArgsConstructorAndSetters() {
        List<StayResponse> content = List.of(new StayResponse(), new StayResponse());
        StayPageResponse page = new StayPageResponse();
        page.setContent(content);
        page.setPage(2);
        page.setSize(10);
        page.setTotalElements(25L);
        page.setTotalPages(3);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(25L);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }
}
