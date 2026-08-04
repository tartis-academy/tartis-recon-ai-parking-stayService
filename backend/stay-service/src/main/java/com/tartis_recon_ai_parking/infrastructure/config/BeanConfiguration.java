package com.tartis_recon_ai_parking.infrastructure.config;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
     * GW-06: usuario que origino la cadena, para que los servicios destino
     * puedan escribir "esto lo pidio operario.test" en vez de "esto lo pidio
     * parking-stay-service". Contexto de log, nunca credencial: ver
     * {@link #tracingContextInterceptor()}.
     */
    public static final String ORIGIN_USER_HEADER = "X-Origin-User";

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

    /**
     * RES-06: builder por defecto para las integraciones internas (spot,
     * tariff, ticket). Connect y read timeout explicitos, ahora leidos de
     * configuracion ({@code services.rest-client.*}) en vez de hardcodeados.
     *
     * <p>Es {@link Primary} porque tres de los cuatro adaptadores lo inyectan
     * sin cualificador; el de vehiculo usa {@link #vehicleRestClientBuilder}.
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${services.rest-client.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${services.rest-client.read-timeout-ms:5000}") long readTimeoutMs) {
        return restClientBuilderWith(authorizedClientManager, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * RES-06 (deuda Fase I): builder especifico para vehicle-service, que en la
     * ruta sincrona de check-in consulta a un proveedor EXTERNO de datos de
     * vehiculo, mas lento que los servicios internos. Comparte el connect
     * timeout, pero usa un read timeout propio y mas holgado
     * ({@code services.rest-client.vehicle.read-timeout-ms}) para no cortar
     * consultas legitimas que tardan mas que el resto de integraciones.
     */
    @Bean
    @Qualifier("vehicleRestClientBuilder")
    public RestClient.Builder vehicleRestClientBuilder(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${services.rest-client.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${services.rest-client.vehicle.read-timeout-ms:8000}") long vehicleReadTimeoutMs) {
        return restClientBuilderWith(authorizedClientManager, connectTimeoutMs, vehicleReadTimeoutMs);
    }

    /**
     * Construye un {@code RestClient.Builder} con los timeouts dados y los
     * interceptores comunes (token de servicio + correlacion GW-06), para que
     * ambos beans compartan exactamente la misma cadena y solo difieran en el
     * read timeout.
     */
    private RestClient.Builder restClientBuilderWith(OAuth2AuthorizedClientManager authorizedClientManager,
                                                     long connectTimeoutMs, long readTimeoutMs) {
        return RestClient.builder()
                .requestFactory(timeoutRequestFactory(connectTimeoutMs, readTimeoutMs))
                .requestInterceptor(bearerTokenInterceptor(authorizedClientManager))
                // GW-06: sin esto la cadena de trazas se corta en el primer
                // salto. Va DESPUES del de token a proposito: si Keycloak
                // falla, el ServiceTokenException se lanza antes y no llegamos
                // a mandar cabecera de correlacion a un sitio al que no vamos
                // a llamar.
                .requestInterceptor(tracingContextInterceptor());
    }

    /**
     * RES-06: fabrica el {@link SimpleClientHttpRequestFactory} con los timeouts
     * indicados en milisegundos. Extraido y con visibilidad de paquete para que
     * el test pueda verificar que los valores configurados se aplican de verdad,
     * sin levantar un servidor.
     */
    static SimpleClientHttpRequestFactory timeoutRequestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeoutMs);
        requestFactory.setReadTimeout((int) readTimeoutMs);
        return requestFactory;
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
     * <p>No se escribe nada en el MDC desde aqui: este interceptor solo lee.
     * Poner claves aqui significaria tener que limpiarlas, y el dueno del ciclo
     * de vida del MDC son los filtros, no el cliente HTTP.
     *
     * <p>Ademas del correlation-id propaga la <strong>identidad de origen</strong>
     * en {@code X-Origin-User}, por un motivo especifico de stay: este servicio
     * pide su propio token con {@code client_credentials} y NO reenvia el del
     * usuario (decision razonada arriba, en {@link #authorizedClientManager}).
     * El efecto colateral es que vehicle, spot, tariff y ticket ven siempre
     * {@code azp=parking-stay-service} y pierden por completo que operario
     * origino la operacion. Sin esta cabecera, el criterio "quien llama a que"
     * quedaria cubierto solo en el primer salto.
     *
     * <p><strong>{@code X-Origin-User} NO es una credencial y no debe usarse
     * para autorizar nada.</strong> La autorizacion de estas llamadas la da el
     * bearer de {@code client_credentials} que puso el interceptor anterior.
     * Esto es contexto de log y nada mas: llega desde otro servicio y no va
     * firmada. Si alguien escribe algun dia un {@code @PreAuthorize} que lea
     * esta cabecera, es un fallo de seguridad.
     *
     * <p>Alternativa descartada por coste: meter el {@code preferred_username}
     * original como claim del token de servicio via token exchange en Keycloak.
     * Es mas limpio conceptualmente y bastante mas caro de montar; para el
     * alcance de GW-06 la cabecera basta.
     */
    private ClientHttpRequestInterceptor tracingContextInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

            String originUser = MDC.get(RequestIdentityFilter.USER_NAME_MDC_KEY);
            if (originUser != null && !originUser.isBlank()) {
                request.getHeaders().set(ORIGIN_USER_HEADER, originUser);
            }

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