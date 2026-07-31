package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.context.annotation.Profile;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    @SuppressWarnings({"java:S112", "java:S1130"}) // Spring Security HttpSecurity.build() declares throws Exception
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .exceptionHandling(eh -> eh
                .accessDeniedHandler((request, response, ex) -> resolver.resolveException(request, response, null, ex))
                .authenticationEntryPoint((request, response, ex) -> resolver.resolveException(request, response, null, ex))
            );
        return http.build();
    }

    /**
     * Ruta del stream de eventos. Es el path que ya asume el frontend
     * ({@code src/lib/use-sse.ts}) y se corresponde con la route
     * {@code stay-service-events-route} reservada en {@code kong/kong.yml}.
     */
    public static final String SSE_PATH = "/v1/events";

    /**
     * GW-06 / SSE-08 - el token por query string se acepta SOLO en la ruta del
     * stream de eventos.
     *
     * <p><strong>Por que hay que permitirlo.</strong> La API {@code EventSource}
     * del navegador no deja poner cabeceras, asi que en un endpoint
     * {@code text/event-stream} no hay forma de mandar
     * {@code Authorization: Bearer}. No es una limitacion del codigo del front:
     * es la API del navegador. La alternativa (cookie de sesion) choca con
     * {@code SessionCreationPolicy.STATELESS}.
     *
     * <p><strong>Por que hay que acotarlo.</strong> Antes esto estaba activado
     * de forma global y CUALQUIER endpoint de stay aceptaba
     * {@code ?access_token=...}: el check-in, el listado de estancias, todo. Un
     * token en la URL acaba en el historial del navegador, en la cabecera
     * {@code Referer} de cualquier recurso externo que cargue esa pagina, en
     * los logs de acceso de Kong y en cualquier proxy del camino. El resto de
     * rutas las consume {@code fetch()}, que si puede mandar cabeceras, asi que
     * esa exposicion no compraba nada.
     *
     * <p><strong>Sobre el nombre del parametro.</strong> Se usa
     * {@code access_token}, que es el del RFC 6750 y el que lee
     * {@link DefaultBearerTokenResolver}. Hoy los tres componentes usan nombres
     * distintos y ninguno coincide: el front manda {@code ?token=}
     * ({@code use-sse.ts}) y el plugin {@code jwt} de Kong espera {@code ?jwt=}
     * por defecto. Al cerrar SSE-08 hay que alinear los tres:
     * <ul>
     *   <li>front: {@code token} -> {@code access_token}</li>
     *   <li>kong.yml, route del SSE: {@code uri_param_names: ["access_token"]}</li>
     * </ul>
     * Se elige este y no otro porque es el unico con el que el resolver de
     * Spring funciona de fabrica, incluida la deteccion de token smuggling
     * (token por cabecera y por query a la vez), que va con test propio.
     *
     * <p><strong>Mitigaciones que acompanan a esta excepcion:</strong>
     * <ul>
     *   <li>{@link RequestLoggingFilter} no registra la query string en ninguna
     *       ruta, y ademas excluye esta del log de acceso.</li>
     *   <li>En Kong, la route del SSE necesita su propio {@code file-log} con
     *       {@code custom_fields_by_lua} redactando {@code request.uri},
     *       {@code request.url} y {@code request.querystring}: el serializer
     *       redacta la cabecera {@code Authorization} de oficio, pero NO la
     *       query string. Lo exige {@code scripts/ci/validate_kong.py}.</li>
     *   <li>El token del stream deberia ser de vida corta. Fuera del alcance de
     *       GW-06; anotado para SSE-08.</li>
     * </ul>
     *
     * <p>Nota: mientras el endpoint SSE no exista, ninguna peticion casa con el
     * matcher y el efecto es que no se acepta el token por URL en ningun sitio,
     * que es el estado mas seguro posible.
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver soloCabecera = new DefaultBearerTokenResolver();

        DefaultBearerTokenResolver tambienQueryString = new DefaultBearerTokenResolver();
        tambienQueryString.setAllowUriQueryParameter(true);

        // EventSource solo hace GET: restringir el metodo reduce la superficie
        // sin coste. AntPathRequestMatcher no vale aqui: se elimino en Spring
        // Security 7, que es la que trae Spring Boot 4.
        RequestMatcher rutaSse = PathPatternRequestMatcher.pathPattern(HttpMethod.GET, SSE_PATH);

        return request -> rutaSse.matches(request)
                ? tambienQueryString.resolve(request)
                : soloCabecera.resolve(request);
    }

    // sin este converter, los roles de realm_access.roles nunca llegan a
    // convertirse en GrantedAuthority con prefijo ROLE_ (ver KeycloakRoleConverter).
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
}
