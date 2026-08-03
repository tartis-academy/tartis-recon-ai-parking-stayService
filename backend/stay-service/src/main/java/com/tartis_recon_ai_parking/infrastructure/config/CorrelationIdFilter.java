package com.tartis_recon_ai_parking.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * GW-06 - trazabilidad de peticiones.
 *
 * <p>Replicado de tariff-service. Es identico en los cinco microservicios a
 * proposito: el nombre de la cabecera y de la clave del MDC tienen que
 * coincidir en todos, o los logs no se pueden cruzar. Si hay que cambiar algo,
 * se cambia en los cinco.
 *
 * <p>Kong genera la cabecera {@code X-Correlation-ID} (plugin
 * {@code correlation-id} en {@code infra/kong/kong.yml}) y la propaga upstream.
 * Este filtro la recoge y la mete en el MDC para que aparezca en todas las
 * lineas de log del servicio, de modo que los logs de Kong y los de los cinco
 * microservicios se puedan unir por ese mismo identificador.
 *
 * <p>Si la cabecera no viene, se genera aqui. Desde GW-07 los microservicios ya
 * no publican puerto de host, asi que este caso cubre sobre todo el arranque en
 * local ({@code mvn spring-boot:run}, puerto 8080) y las llamadas dentro de la
 * red {@code parking-shared} que no pasan por el gateway: tambien esas
 * peticiones deben ser trazables.
 *
 * <p>Se registra con {@link Ordered#HIGHEST_PRECEDENCE} a proposito, para que
 * el identificador exista antes que cualquier otro filtro. En particular antes
 * que el {@code SecurityFilterChain} (orden -100), de forma que un rechazo con
 * 401 o 403 tambien quede trazado - que es justo donde mas falta hace. Es el
 * mismo criterio que en Kong, donde el plugin {@code correlation-id} tiene la
 * prioridad mas alta (100001) y corre antes que {@code jwt} (1450).
 *
 * <p><strong>Especifico de stay-service</strong>: este es el unico microservicio
 * con llamadas salientes (vehicle, spot, tariff y ticket). El identificador que
 * este filtro deja en el MDC lo recoge el interceptor de
 * {@code BeanConfiguration#restClientBuilder} y lo reenvia en la cabecera de
 * esas cuatro llamadas. Sin ese interceptor la cadena se cortaria en el primer
 * salto y cada servicio destino generaria su propio identificador.
 *
 * <p><strong>Lo que este filtro NO hace</strong>: identificar al usuario. El
 * {@code sub} del JWT solo esta disponible despues de que Spring Security haya
 * autenticado, asi que la identidad va en un filtro aparte registrado despues
 * del de seguridad. No se puede juntar en este sin perder la traza de los 401.
 *
 * <p>Recordatorio de por que la identidad no puede venir de Kong: el plugin
 * {@code jwt} casa el token contra el consumer cuya {@code key} es el claim
 * {@code iss}, y los tres usuarios del realm salen del mismo issuer. Las
 * cabeceras {@code X-Consumer-*} que anade Kong valen igual para un ADMIN que
 * para un USER. Ver {@code docs/adr/0001} en el repo de infra.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Debe coincidir con {@code header_name} del plugin correlation-id de Kong. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** Clave del MDC. Debe coincidir con el %X{...} de logging.pattern.correlation. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * La cabecera llega de fuera y va directa a los logs, asi que se valida
     * antes de aceptarla. Sin esto, un cliente podria colar saltos de linea y
     * fabricar entradas de log falsas (log injection / CRLF), o mandar una
     * cadena enorme y engordar los ficheros.
     *
     * <p>Kong genera UUIDs (o uuid#counter, que anade "#N"), asi que este
     * conjunto de caracteres cubre de sobra el trafico legitimo.
     */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._#:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request.getHeader(CORRELATION_ID_HEADER));

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        // Se devuelve siempre al cliente, no solo cuando Kong la ha generado:
        // asi el frontend puede mostrarla en un error y el usuario pegarla en
        // un ticket de soporte.
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Imprescindible: el hilo vuelve al pool y sin esto la siguiente
            // peticion que lo reutilice heredaria este identificador. Se quita
            // solo esta clave, no MDC.clear(), para no pisar lo que hayan
            // puesto otros filtros (el de identidad, cuando exista).
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private String resolveCorrelationId(String incoming) {
        if (incoming != null && SAFE_CORRELATION_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
