package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

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
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(bearerEntryPoint(resolver))
            )
            .exceptionHandling(eh -> eh
                // Defensivo: hoy ningun camino a nivel de filtros produce un 403
                // (todo el denegado sale por @PreAuthorize dentro del
                // DispatcherServlet, que ya cae en el resolver). Se deja como
                // seguro por si alguien anade hasRole a authorizeHttpRequests.
                .accessDeniedHandler((request, response, ex) -> resolver.resolveException(request, response, null, ex))
                .authenticationEntryPoint(bearerEntryPoint(resolver))
            );
        return http.build();
    }

    /**
     * Ruta del stream de eventos. Es el path que ya asume el frontend
     * ({@code src/lib/use-sse.ts}) y se corresponde con la route
     * {@code stay-service-events-route} de {@code kong/kong.yml}.
     */
    public static final String SSE_PATH = "/v1/events";

    // Compone el BearerTokenAuthenticationEntryPoint por defecto (que fija el
    // status y la cabecera WWW-Authenticate, RFC 6750) con la delegacion al
    // resolver para que el cuerpo sea el ErrorResponse del adapter. Sin esto,
    // el entry point del oauth2ResourceServer no emite la cabecera y el
    // cliente no puede distinguir 401 (token caducado) de 403 (sin rol).
    private AuthenticationEntryPoint bearerEntryPoint(HandlerExceptionResolver resolver) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, ex) -> {
            bearer.commence(request, response, ex);
            resolver.resolveException(request, response, null, ex);
        };
    }

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
     * <p><strong>Sobre el nombre del parametro.</strong> {@code access_token} y
     * solo ese, en los tres componentes de la cadena: el front lo manda asi
     * ({@code use-sse.ts}) y la route {@code stay-service-events-route} de
     * {@code kong/kong.yml} declara {@code uri_param_names: ["access_token"]}.
     * Es el nombre del RFC 6750 y el unico que {@link DefaultBearerTokenResolver}
     * lee de fabrica, con su deteccion de token smuggling incluida. NO se acepta
     * {@code ?jwt=} (el default del plugin de Kong): un segundo nombre obliga a
     * reimplementar a mano esa deteccion y no lo usa ningun cliente.
     *
     * <p><strong>Mitigaciones que acompanan a esta excepcion:</strong>
     * <ul>
     *   <li>{@link RequestLoggingFilter} no registra la query string en ninguna
     *       ruta, y ademas excluye esta del log de acceso.</li>
     *   <li>En Kong, la route del SSE declara su propio {@code file-log} con
     *       {@code custom_fields_by_lua} sobre los CUATRO campos del serializer
     *       que arrastran la query string: {@code request.uri},
     *       {@code request.url}, {@code request.querystring} y
     *       {@code upstream_uri}. El serializer redacta la cabecera
     *       {@code Authorization} de oficio, pero NO la query string. Lo exige
     *       {@code scripts/ci/validate_kong.py}.</li>
     *   <li>El token del stream deberia ser de vida corta. Sigue pendiente: hoy
     *       es el mismo token de sesion que usa el resto del front.</li>
     * </ul>
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver soloCabecera = new DefaultBearerTokenResolver();

        DefaultBearerTokenResolver tambienQueryString = new DefaultBearerTokenResolver();
        tambienQueryString.setAllowUriQueryParameter(true);

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
