package com.tartis_recon_ai_parking.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GW-06 - la mitad de "quien llama a que" que {@link CorrelationIdFilter} no
 * puede cubrir.
 *
 * <p>Va en un filtro separado y no dentro de aquel por una razon concreta:
 * {@code CorrelationIdFilter} corre en {@code HIGHEST_PRECEDENCE} para que los
 * 401 y 403 tambien queden trazados, y en ese punto Spring Security todavia no
 * ha autenticado, asi que no hay JWT que leer. Este corre despues de la cadena
 * de seguridad, donde el {@code SecurityContext} ya esta poblado. Juntarlos
 * significaria perder la traza de los rechazos, que es justo donde mas falta
 * hace.
 *
 * <p><strong>El orden.</strong> La cadena de Spring Security se registra en
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER} (-100), asi que cualquier
 * valor por encima corre despues. Se usa 0 y no un numero mas alto porque no
 * hay nada mas compitiendo por esa franja; {@link RequestLoggingFilter} va
 * justo detras, en 1, para que su linea salga ya con la identidad puesta.
 *
 * <p><strong>Que se mete en el MDC y por que:</strong>
 * <ul>
 *   <li>{@code userId} = claim {@code sub}. Estable y unico, es el que sirve
 *       para correlacionar de verdad.</li>
 *   <li>{@code userName} = {@code preferred_username}. Legible por humanos;
 *       para leer un log a las tres de la manana vale mas que un UUID.</li>
 *   <li>{@code clientId} = {@code azp}. Distingue si la llamada viene de un
 *       usuario a traves del frontend o de otro microservicio con
 *       client_credentials (p.ej. {@code parking-stay-service}).</li>
 *   <li>{@code roles} = las authorities ya convertidas por
 *       {@link KeycloakRoleConverter}, sin el prefijo {@code ROLE_}. Sin esto
 *       no se puede auditar por que salio un 403.</li>
 * </ul>
 *
 * <p><strong>No se mete el token, ni entero ni truncado. Nunca.</strong> Un
 * JWT en los logs es una credencial en texto plano al alcance de cualquiera
 * con {@code docker logs}.
 *
 * <p>Recordatorio de por que la identidad no puede venir de Kong: el plugin
 * {@code jwt} casa el token contra el consumer cuya {@code key} es el claim
 * {@code iss}, y los tres usuarios del realm salen del mismo issuer, asi que
 * las cabeceras {@code X-Consumer-*} valen igual para un ADMIN que para un
 * USER. Ver {@code docs/adr/0001} en el repo de infra.
 *
 * <p><strong>Aviso sobre el perfil dev</strong>: {@link SecurityConfigDev} hace
 * {@code anyRequest().permitAll()} sin resource server, asi que en dev nunca
 * hay {@code JwtAuthenticationToken} y este filtro no mete nada. Es correcto y
 * esperado, pero significa que esto solo se valida con Keycloak levantado.
 */
@Component
@Order(RequestIdentityFilter.ORDER)
public class RequestIdentityFilter extends OncePerRequestFilter {

    /** Despues de la cadena de seguridad (-100) y antes del log de acceso (1). */
    public static final int ORDER = 0;

    public static final String USER_ID_MDC_KEY = "userId";
    public static final String USER_NAME_MDC_KEY = "userName";
    public static final String CLIENT_ID_MDC_KEY = "clientId";
    public static final String ROLES_MDC_KEY = "roles";
    public static final String ORIGIN_USER_MDC_KEY = "originUser";

    /**
     * Usuario que origino la cadena, cuando quien llama es otro microservicio.
     * La pone stay-service en sus llamadas salientes (ver
     * {@code BeanConfiguration#tracingContextInterceptor}).
     */
    public static final String ORIGIN_USER_HEADER = "X-Origin-User";

    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
    private static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * {@link #ORIGIN_USER_HEADER} llega de fuera y va directa a los logs, asi
     * que se valida antes de aceptarla: sin esto un cliente podria colar saltos
     * de linea y fabricar entradas de log falsas (log injection / CRLF), o
     * mandar una cadena enorme y engordar los ficheros. Mismo criterio que
     * {@link CorrelationIdFilter}, ampliado con el punto y la arroba porque
     * aqui viaja un {@code preferred_username} de Keycloak.
     */
    private static final Pattern SAFE_ORIGIN_USER = Pattern.compile("[A-Za-z0-9._@-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            populateMdc();
            populateOriginUser(request.getHeader(ORIGIN_USER_HEADER));
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ORIGIN_USER_MDC_KEY);
            // El hilo vuelve al pool: sin esto la siguiente peticion que lo
            // reutilice heredaria esta identidad y los logs mentirian sobre
            // quien hizo que. Se quitan solo estas claves, no MDC.clear(),
            // para no pisar el correlationId que puso el filtro anterior.
            MDC.remove(USER_ID_MDC_KEY);
            MDC.remove(USER_NAME_MDC_KEY);
            MDC.remove(CLIENT_ID_MDC_KEY);
            MDC.remove(ROLES_MDC_KEY);
        }
    }

    private void populateMdc() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            // Peticion anonima (/actuator/health) o perfil dev con permitAll:
            // se deja el hueco vacio, no se inventa nada.
            return;
        }

        Jwt jwt = jwtAuthentication.getToken();
        putIfPresent(USER_ID_MDC_KEY, jwt.getSubject());
        putIfPresent(USER_NAME_MDC_KEY, jwt.getClaimAsString(PREFERRED_USERNAME_CLAIM));
        putIfPresent(CLIENT_ID_MDC_KEY, jwt.getClaimAsString(AUTHORIZED_PARTY_CLAIM));
        putIfPresent(ROLES_MDC_KEY, joinRoles(jwtAuthentication));
    }

    /**
     * Se ordenan alfabeticamente a proposito: el orden que devuelve Keycloak en
     * {@code realm_access.roles} no esta garantizado, y sin ordenar la misma
     * peticion del mismo usuario puede salir como "ADMIN,OPERARIO" o
     * "OPERARIO,ADMIN" en dos lineas distintas, lo que rompe cualquier
     * agrupacion posterior sobre el campo.
     */
    /**
     * Recoge la identidad del usuario que origino la cadena cuando la llamada
     * viene de otro microservicio.
     *
     * <p>Hace falta porque stay-service llama a los demas con su propio token
     * de {@code client_credentials}: sin esta cabecera, vehicle, spot, tariff y
     * ticket ven siempre {@code azp=parking-stay-service} y pierden por
     * completo que operario origino la operacion, con lo que "quien llama a
     * que" quedaria cubierto solo en el primer salto.
     *
     * <p><strong>Es contexto NO confiable.</strong> No va firmada y la manda
     * otro servicio, asi que solo sirve para escribir logs. No debe usarse para
     * autorizar nada: quien autoriza es el JWT. Se guarda en una clave del MDC
     * distinta ({@code originUser}) precisamente para que no se confunda con
     * {@code userName}, que si sale del token verificado.
     *
     * <p>Solo tiene sentido leerla cuando {@code clientId} es una cuenta de
     * servicio. Si llega en una peticion de usuario normal, es ruido o un
     * intento de despistar, y por eso el valor se sanea antes de registrarlo.
     */
    private static void populateOriginUser(String incoming) {
        if (incoming != null && SAFE_ORIGIN_USER.matcher(incoming).matches()) {
            MDC.put(ORIGIN_USER_MDC_KEY, incoming);
        }
    }

    private static String joinRoles(JwtAuthenticationToken jwtAuthentication) {
        return jwtAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
