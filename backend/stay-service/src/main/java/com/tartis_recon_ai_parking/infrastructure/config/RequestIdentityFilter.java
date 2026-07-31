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

    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
    private static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            populateMdc();
            filterChain.doFilter(request, response);
        } finally {
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
