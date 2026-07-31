package com.tartis_recon_ai_parking.infrastructure.config;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

import com.tartis_recon_ai_parking.domain.stay.exception.ServiceTokenException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class BeanConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Id del registro de {@code application.properties}, no el client-id real
     * de Keycloak (ese llega por entorno, {@code STAY_CLIENT_ID}).
     */
    private static final String REGISTRATION_ID = "parking-stay";

    /**
     * stay-service es el unico microservicio que llama a otros por HTTP
     * (vehicle, spot, tariff y ticket) y esas cuatro APIs exigen JWT. Como no
     * mandaba ninguna cabecera Authorization, las cuatro respondian 401 y el
     * check-in fallaba al 100%.
     *
     * <p>No se reenvia el token del usuario que entra por Kong, por dos
     * motivos: no siempre hay un usuario detras (los consumidores de RabbitMQ
     * y las tareas internas no traen ninguno), y aunque lo hubiera su rol no
     * tiene por que cubrir lo que stay necesita — el auto-alta de vehiculo es
     * {@code POST /v1/vehicles}, ADMIN-only, asi que un OPERARIO haciendo
     * check-in se comeria un 403. Por eso stay pide su propio token con
     * client_credentials.
     *
     * <p>El manager cachea el token y lo renueva solo al caducar, asi que esto
     * no supone una llamada a Keycloak por peticion.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientService authorizedClients) {

        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
        manager.setAuthorizedClientProvider(provider);

        return manager;
    }

    @Bean
    public RestClient.Builder restClientBuilder(OAuth2AuthorizedClientManager authorizedClientManager) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        // Timeout de conexión: tiempo máximo para conectar con el microservicio (3 segundos)
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());

        // Timeout de lectura: tiempo máximo esperando la respuesta (5 segundos)
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        // Los cuatro adaptadores de salida (StayVehicleClientAdapter y
        // companeros) construyen su RestClient a partir de este builder, asi
        // que el interceptor cubre las cuatro integraciones de una vez. Si
        // manana aparece una quinta, queda cubierta sin tocar nada.
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(bearerTokenInterceptor(authorizedClientManager))
                // GW-06: sin esto la cadena de trazas se corta en el primer
                // salto. Va DESPUES del de token a proposito: si Keycloak
                // falla, el ServiceTokenException se lanza antes y no llegamos
                // a mandar cabecera de correlacion a un sitio al que no vamos
                // a llamar.
                .requestInterceptor(correlationIdInterceptor());
    }

    /**
     * GW-06 - propaga el correlation-id de esta peticion a los microservicios
     * destino.
     *
     * <p>stay-service es el unico de los cinco con llamadas salientes, asi que
     * es el unico sitio donde hace falta este interceptor y tambien el unico
     * donde su ausencia se nota: sin el, un check-in genera cinco lineas de
     * traza con cinco identificadores distintos, porque vehicle, spot, tariff
     * y ticket no reciben cabecera y cada uno genera el suyo
     * ({@link CorrelationIdFilter} hace exactamente eso cuando no le llega).
     *
     * <p>Lee del MDC, que es donde lo dejo {@link CorrelationIdFilter}. El MDC
     * de SLF4J es ThreadLocal y las llamadas salientes de stay son sincronas
     * (RestClient bloqueante sobre el hilo del servlet), asi que el valor esta
     * disponible aqui sin necesidad de pasarlo por parametro por toda la
     * aplicacion.
     *
     * <p><strong>Cuidado si algun dia esto se vuelve asincrono</strong>
     * ({@code @Async}, WebClient reactivo, CompletableFuture con otro
     * executor): el MDC NO se hereda al cambiar de hilo y este interceptor
     * empezaria a mandar un identificador nuevo en cada llamada, en silencio.
     * En ese momento hay que envolver el executor con un TaskDecorator que
     * copie el MDC.
     *
     * <p>El caso "no hay nada en el MDC" es real y esperado: los consumidores
     * de RabbitMQ corren en hilos del listener container, no en un hilo de
     * servlet, asi que ahi no hubo CorrelationIdFilter. Se genera uno nuevo en
     * vez de mandar la cabecera vacia, porque una traza parcial vale mas que
     * ninguna. Cuando la correlacion cruce AMQP (propiedad estandar
     * {@code correlationId} del mensaje) este caso deberia dejar de darse.
     *
     * <p>No se escribe el identificador en el MDC desde aqui: este interceptor
     * solo lee. Poner la clave aqui significaria tener que limpiarla, y el
     * dueno del ciclo de vida del MDC es el filtro, no el cliente HTTP.
     */
    private ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            return execution.execute(request, body);
        };
    }

    private ClientHttpRequestInterceptor bearerTokenInterceptor(OAuth2AuthorizedClientManager manager) {
        return (request, body, execution) -> {
            OAuth2AuthorizedClient authorizedClient;

            try {
                authorizedClient = manager.authorize(
                        OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                                .principal(REGISTRATION_ID)
                                .build());
            } catch (OAuth2AuthorizationException e) {
                // Keycloak rechaza las credenciales (secreto mal puesto, cliente
                // que no existe) o no responde. Se traduce a una excepcion de
                // dominio para que el handler la mapee a 503, igual que el
                // resto de fallos de integracion (IN-36); si sale cruda, el
                // handler generico responde "Ha ocurrido un error inesperado"
                // y el mensaje util se queda solo en el log.
                throw new ServiceTokenException(errorMessage(), e);
            }

            if (authorizedClient == null) {
                // El manager devuelve null si el registro no esta configurado.
                throw new ServiceTokenException(errorMessage());
            }

            request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
            return execution.execute(request, body);
        };
    }

    private static String errorMessage() {
        return "stay-service no pudo obtener su token de servicio (registro '" + REGISTRATION_ID
                + "'). Revisar STAY_CLIENT_ID / STAY_CLIENT_SECRET, que coincidan con el cliente"
                + " parking-stay-service del realm, y que Keycloak responda en KEYCLOAK_TOKEN_URI.";
    }
}
