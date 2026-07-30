package com.tartis_recon_ai_parking.infrastructure.config;

import java.time.Duration;

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
import org.springframework.web.client.RestClient;

@Configuration
public class BeanConfiguration {

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
                .requestInterceptor(bearerTokenInterceptor(authorizedClientManager));
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
                // que no existe) o no responde. Sin este catch sale la
                // OAuth2AuthorizationException cruda, el handler generico la
                // convierte en "Ha ocurrido un error inesperado" y quien
                // depura no tiene por donde empezar.
                throw new IllegalStateException(errorMessage(), e);
            }

            if (authorizedClient == null) {
                // El manager devuelve null si el registro no esta configurado.
                throw new IllegalStateException(errorMessage());
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
