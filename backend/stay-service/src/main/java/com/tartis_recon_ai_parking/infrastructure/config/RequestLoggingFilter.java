package com.tartis_recon_ai_parking.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * GW-06 - una linea de log por peticion HTTP. Cubre el criterio "registrar
 * logging de peticiones".
 *
 * <p>Orden 1: justo detras de {@link RequestIdentityFilter} (orden 0), para que
 * la linea salga ya con el usuario y los roles disponibles en el MDC.
 *
 * <p>Formato de la linea, pensado para grep y para agrupar por campo:
 * <pre>
 * http.access user=operario.test roles=OPERARIO client=parking-frontend
 *             method=POST path=/v1/stays/check-in status=201 duration_ms=207
 * </pre>
 *
 * <p>La identidad se repite aqui aunque tambien acabe en el prefijo de todas
 * las lineas cuando se amplie {@code logging.pattern.correlation}: una linea de
 * acceso tiene que ser autocontenida. Es la que se extrae para auditar y la que
 * se manda a un agregador; depender del prefijo la haria inservible fuera de
 * su contexto.
 *
 * <p><strong>Por que se registra {@code getRequestURI()} y NUNCA la query
 * string.</strong> stay-service acepta el token por parametro de URL
 * ({@code setAllowUriQueryParameter(true)} en {@link SecurityConfig}, necesario
 * para SSE-08 porque {@code EventSource} no permite mandar cabeceras).
 * Registrar la query string escribiria JWTs completos en texto plano en los
 * logs del contenedor, donde se quedan indefinidamente y los ve cualquiera con
 * acceso a {@code docker logs}. {@code getRequestURI()} devuelve el path sin
 * query: es la llamada correcta y hay que dejarlo asi. Es el mismo cuidado que
 * en Kong, donde el {@code file-log} de la ruta SSE tiene que redactar
 * {@code request.uri}, {@code request.url} y {@code request.querystring} (lo
 * exige {@code scripts/ci/validate_kong.py} en el repo de infra).
 *
 * <p>Si alguna vez hace falta el detalle de los parametros para depurar, se
 * registran los nombres, nunca los valores.
 *
 * <p><strong>Alternativa descartada</strong>: {@code CommonsRequestLoggingFilter}
 * de Spring. Con {@code setIncludeQueryString(true)} escribe el token, y con
 * {@code false} pierde informacion que si queremos. Ademas no da ni la latencia
 * ni el status, que son la mitad del valor de una linea de acceso.
 */
@Component
@Order(RequestLoggingFilter.ORDER)
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** Justo detras de {@link RequestIdentityFilter}. */
    public static final int ORDER = RequestIdentityFilter.ORDER + 1;

    /**
     * Logger con nombre propio, no el de la clase: permite silenciar el log de
     * acceso ({@code logging.level.http.access=WARN}) sin perder los logs de
     * negocio, o mandarlo a otro appender el dia que haya agregacion
     * centralizada.
     */
    private static final Logger log = LoggerFactory.getLogger("http.access");

    private static final String ANONYMOUS = "anonymous";

    /**
     * Rutas que no generan linea de acceso.
     *
     * <p>Los healthcheck de Docker pegan cada pocos segundos y ahogarian el log
     * util. La ruta SSE se excluye porque es una conexion de larga duracion:
     * una sola linea al cerrarse, con una duracion de cuarenta minutos, no
     * informa de nada y ademas se escribiria tardisimo. Su trazabilidad es
     * apertura y cierre de stream, que corresponde a SSE-08.
     */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/actuator/health",
            "/v1/stays/events",
            "/openapi.yml",
            "/swagger-ui",
            "/v3/api-docs");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // En el finally, no despues del doFilter: si la cadena lanza, la
            // peticion tambien tiene que dejar linea. Es justo la que interesa.
            log.info("user={} roles={} client={} method={} path={} status={} duration_ms={}",
                    mdcOrDefault(RequestIdentityFilter.USER_NAME_MDC_KEY, ANONYMOUS),
                    mdcOrDefault(RequestIdentityFilter.ROLES_MDC_KEY, "-"),
                    mdcOrDefault(RequestIdentityFilter.CLIENT_ID_MDC_KEY, "-"),
                    request.getMethod(),
                    // Path SIN query string. Ver el javadoc de la clase: no
                    // cambiar por getRequestURL() + getQueryString().
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - startNanos) / 1_000_000);
        }
    }

    private static String mdcOrDefault(String key, String fallback) {
        String value = MDC.get(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
