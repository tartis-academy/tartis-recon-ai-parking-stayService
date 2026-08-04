package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GW-06 - el correlation-id tiene que salir en las llamadas a vehicle, spot,
 * tariff y ticket.
 *
 * <p>Se prueba sobre el {@code RestClient.Builder} de
 * {@link BeanConfiguration}, no sobre cada uno de los cuatro adaptadores de
 * salida: los cuatro construyen su cliente a partir de ese unico builder, asi
 * que cubrir el builder cubre las cuatro integraciones - y tambien la quinta
 * que aparezca manana sin tocar este test.
 */
class BeanConfigurationCorrelationTest {

    private RestClient restClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        // El interceptor de token corre ANTES que el de correlacion, asi que
        // sin un manager que devuelva algo el bearerTokenInterceptor lanzaria
        // ServiceTokenException y nunca llegariamos a comprobar la cabecera.
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-de-servicio-de-prueba",
                Instant.now(),
                Instant.now().plusSeconds(300));

        OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
        when(authorizedClient.getAccessToken()).thenReturn(accessToken);

        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(authorizedClient);

        // RES-06: restClientBuilder ahora recibe los timeouts (connect, read) en ms.
        // Aqui el test solo comprueba la propagacion del correlation-id (GW-06),
        // asi que los valores concretos son irrelevantes; se pasan los por defecto.
        RestClient.Builder builder = new BeanConfiguration().restClientBuilder(manager, 3000L, 5000L);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    /**
     * El MDC es estatico y por hilo: si un test lo deja sucio, contamina a los
     * siguientes. Se limpia siempre, aunque el test falle.
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Debe propagar a spot-service el correlation-id que dejo CorrelationIdFilter en el MDC")
    void shouldPropagateCorrelationIdFromMdc() {
        String fromKong = "3f2a9c11-8e4d-4b7a-9c1e-2d5f6a7b8c90";
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, fromKong);

        server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(CorrelationIdFilter.CORRELATION_ID_HEADER, fromKong))
                .andRespond(withSuccess());

        restClient.post()
                .uri("http://spot-service:8080/v1/spots/occupy")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }

    @Test
    @DisplayName("Debe seguir mandando el bearer de client_credentials ademas del correlation-id")
    void shouldKeepSendingTheServiceBearerToken() {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "abc-123");

        server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
                .andExpect(header("Authorization", "Bearer token-de-servicio-de-prueba"))
                .andExpect(header(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123"))
                .andRespond(withSuccess());

        restClient.get()
                .uri("http://vehicle-service:8080/v1/vehicles")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }

    /**
     * Este es el test que de verdad protege: es el escenario del consumidor de
     * RabbitMQ (hilo del listener container, sin paso por CorrelationIdFilter)
     * y es el unico que se rompe en silencio - la llamada sale igual, solo que
     * sin traza y sin que falle nada.
     */
    @Test
    @DisplayName("Debe generar un correlation-id nuevo si el MDC esta vacio, nunca mandar la cabecera vacia o ausente")
    void shouldGenerateCorrelationIdWhenMdcIsEmpty() {
        AtomicReference<String> sent = new AtomicReference<>();

        server.expect(requestTo("http://tariff-service:8080/v1/tariffs"))
                .andExpect(request -> sent.set(
                        request.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)))
                .andRespond(withSuccess());

        restClient.get()
                .uri("http://tariff-service:8080/v1/tariffs")
                .retrieve()
                .toBodilessEntity();

        server.verify();
        assertNotNull(sent.get(), "sin MDC tambien tiene que salir cabecera: una traza parcial vale mas que ninguna");
        assertDoesNotThrow(() -> UUID.fromString(sent.get()),
                "el identificador generado debe ser un UUID, igual que el que genera CorrelationIdFilter");
    }

    @Test
    @DisplayName("Debe propagar la identidad de origen en X-Origin-User")
    void shouldPropagateOriginUser() {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "abc-123");
        MDC.put(RequestIdentityFilter.USER_NAME_MDC_KEY, "operario.test");

        server.expect(requestTo("http://vehicle-service:8080/v1/vehicles"))
                // stay llama con su propio token de client_credentials, asi que
                // vehicle ve azp=parking-stay-service. Sin esta cabecera pierde
                // por completo que operario origino la operacion.
                .andExpect(header(BeanConfiguration.ORIGIN_USER_HEADER, "operario.test"))
                .andRespond(withSuccess());

        restClient.get()
                .uri("http://vehicle-service:8080/v1/vehicles")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }

    /**
     * Caso real: los consumidores de RabbitMQ y las tareas internas no tienen
     * usuario detras. Mandar la cabecera vacia seria peor que no mandarla,
     * porque el servicio destino la registraria como si fuera un usuario.
     */
    @Test
    @DisplayName("No debe mandar X-Origin-User si no hay usuario en el MDC")
    void shouldOmitOriginUserWhenAbsent() {
        server.expect(requestTo("http://spot-service:8080/v1/spots/occupy"))
                .andExpect(request -> assertNull(
                        request.getHeaders().getFirst(BeanConfiguration.ORIGIN_USER_HEADER),
                        "sin usuario la cabecera no debe existir, ni siquiera vacia"))
                .andRespond(withSuccess());

        restClient.post()
                .uri("http://spot-service:8080/v1/spots/occupy")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }

    @Test
    @DisplayName("Dos llamadas sin MDC no deben compartir identificador")
    void shouldGenerateDistinctIdsForCallsWithoutMdc() {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        server.expect(requestTo("http://ticket-service:8080/v1/entry-tickets"))
                .andExpect(request -> first.set(
                        request.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)))
                .andRespond(withSuccess());
        server.expect(requestTo("http://ticket-service:8080/v1/tickets"))
                .andExpect(request -> second.set(
                        request.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)))
                .andRespond(withSuccess());

        restClient.get().uri("http://ticket-service:8080/v1/entry-tickets").retrieve().toBodilessEntity();
        restClient.get().uri("http://ticket-service:8080/v1/tickets").retrieve().toBodilessEntity();

        server.verify();
        assertNotEquals(first.get(), second.get());
    }

    /**
     * Una cabecera que ya llegase puesta por otra via no debe duplicarse: se
     * usa {@code set}, no {@code add}. Con {@code add} el servicio destino
     * recibiria "id1,id2" y CorrelationIdFilter lo descartaria por no casar con
     * el patron, generando uno nuevo y cortando la traza igualmente.
     */
    @Test
    @DisplayName("No debe duplicar la cabecera si ya venia puesta en la peticion saliente")
    void shouldNotDuplicateHeader() {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "el-bueno");

        server.expect(requestTo("http://spot-service:8080/v1/spots"))
                .andExpect(request -> {
                    var values = request.getHeaders().get(CorrelationIdFilter.CORRELATION_ID_HEADER);
                    assertNotNull(values);
                    assertEquals(1, values.size(), "la cabecera debe ir una sola vez");
                    assertEquals("el-bueno", values.get(0));
                })
                .andRespond(withSuccess());

        restClient.get()
                .uri("http://spot-service:8080/v1/spots")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "uno-viejo")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }
}
