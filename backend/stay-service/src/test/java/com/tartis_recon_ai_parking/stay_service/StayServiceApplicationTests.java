package com.tartis_recon_ai_parking.stay_service;

import com.tartis_recon_ai_parking.StayServiceApplication;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mockStatic;

// Smoke test: verifica que el contexto de Spring arranca por completo.
// Se ejecuta con el perfil "test" (H2 en memoria) para no depender de un
// Postgres externo ni heredar la configuracion del perfil dev.
@SpringBootTest

@ActiveProfiles("test") // <-- ¡Asegúrate de tener esta anotación!


class StayServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldLoadContextAndRunMain() {
        try (MockedStatic<SpringApplication> mockedSpringApplication = mockStatic(SpringApplication.class)) {
            mockedSpringApplication.when(() -> SpringApplication.run(StayServiceApplication.class, new String[]{}))
                    .thenReturn(null);

            StayServiceApplication.main(new String[]{});

            mockedSpringApplication.verify(() -> SpringApplication.run(StayServiceApplication.class, new String[]{}));
        }
    }
}