package com.tartis_recon_ai_parking.stay_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Smoke test: verifica que el contexto de Spring arranca por completo.
// Se ejecuta con el perfil "test" (H2 en memoria) para no depender de un
// Postgres externo ni heredar la configuracion del perfil dev.
@SpringBootTest
@ActiveProfiles("test")
class StayServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
