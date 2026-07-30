package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output;

import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoActiveTariffException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.ServiceTokenException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;
import com.tartis_recon_ai_parking.domain.stay.exception.TariffServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleDeactivatedException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleServiceException;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.time.Instant;
import java.util.stream.Collectors;


/**
 * Unico punto que traduce excepciones de dominio/aplicacion a respuestas HTTP
 * (invariante IN-36). Ningun caso de uso ni entidad conoce codigos de estado.
 *
 * <p>Codigos del check-in ({@code POST /v1/stays/check-in}), segun el openapi.yml:
 * <ul>
 *   <li><b>400</b> — matricula vacia / tipo de vehiculo invalido / validacion de campos
 *       / matricula rechazada por vehicle-service (formato invalido)</li>
 *   <li><b>404</b> — estancia inexistente (consultas)</li>
 *   <li><b>409</b> — parking completo (RN-01), vehiculo ya dentro (IN-02, CB-05)
 *       o sin tarifa activa configurada (IN-08)</li>
 *   <li><b>422</b> — vehiculo dado de baja (RN-11)</li>
 *   <li><b>503</b> — un servicio externo (spot/tariff/ticket/vehicle) no responde
 *       o falla por un motivo que no es de negocio</li>
 *   <li><b>500</b> — cualquier otra excepcion no anticipada (red de seguridad)</li>
 * </ul>
 *
 * <p>Los tres casos de denegacion (409, 422) comparten consecuencia fisica: la
 * <b>barrera permanece cerrada</b> y no se crea ninguna estancia.
 */
@RestControllerAdvice
public class CustomizedExceptionAdapter {

    private static final Logger log = LoggerFactory.getLogger(CustomizedExceptionAdapter.class);

    /** RN-01 / CA-01 de HU-01: parking completo para ese tipo de vehiculo. */
    @ExceptionHandler(NoAvailableSpotException.class)
    public ResponseEntity<ErrorResponse> handleNoSpot(NoAvailableSpotException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** IN-02 / CB-05: el vehiculo ya consta dentro del parking. */
    @ExceptionHandler(DuplicateActiveStayException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateStay(DuplicateActiveStayException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** RN-11: vehiculo dado de baja. */
    @ExceptionHandler(VehicleDeactivatedException.class)
    public ResponseEntity<ErrorResponse> handleVehicleDeactivated(VehicleDeactivatedException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(StayNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStayNotFound(StayNotFoundException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStayException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStay(InvalidStayException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

 
    /**
     * spot-service caido, con timeout o devolviendo un error que no es de
     * negocio. Se traduce a 503 en vez de dejar pasar la excepcion cruda de red
     * (IN-36): el frontend puede mostrar "servicio de plazas no disponible" en
     * vez de un error generico sin mensaje interpretable.
     */
    @ExceptionHandler(SpotServiceException.class)
    public ResponseEntity<ErrorResponse> handleSpotServiceUnavailable(SpotServiceException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /** IN-08: tariff-service respondio correctamente pero no hay tarifa activa. */
    @ExceptionHandler(NoActiveTariffException.class)
    public ResponseEntity<ErrorResponse> handleNoActiveTariff(NoActiveTariffException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    
    /**
     * tariff-service caido, con timeout, con error 5xx, o incumpliendo su propio
     * contrato (respuesta 200 sin importe). Igual que {@link SpotServiceException},
     * nunca debe llegar sin traducir al frontend (IN-36).
     */
    @ExceptionHandler(TariffServiceException.class)
    public ResponseEntity<ErrorResponse> handleTariffServiceUnavailable(TariffServiceException ex,
                                                                        HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }


    /**
     * ticket-service caido, con timeout, con error 5xx, o incumpliendo su propio
     * contrato (sin ticket de entrada, o sin uniqueId al emitir uno de salida).
     * Igual que {@link SpotServiceException} and {@link TariffServiceException},
     * nunca debe llegar sin traducir al frontend (IN-36).
     */
    @ExceptionHandler(TicketServiceException.class)
    public ResponseEntity<ErrorResponse> handleTicketServiceUnavailable(TicketServiceException ex,
                                                                        HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

 
    /**
     * vehicle-service caido, con timeout, con error 5xx, o incumpliendo su
     * propio contrato (respuesta sin uniqueId). El 404 de "matricula no
     * registrada todavia" NO pasa por aqui: es negocio y ya se resuelve dentro
     * del propio adaptador. Igual que los demas servicios externos, nunca debe
     * llegar sin traducir al frontend (IN-36).
     */
    @ExceptionHandler(VehicleServiceException.class)
    public ResponseEntity<ErrorResponse> handleVehicleServiceUnavailable(VehicleServiceException ex,
                                                                         HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /**
     * stay-service no pudo obtener su token de servicio (Keycloak caido o
     * credenciales de cliente mal configuradas). Es un fallo de integracion
     * como los de arriba y se traduce igual, a 503: sin este handler caia en
     * la red de seguridad generica y el operador solo veia "Ha ocurrido un
     * error inesperado" (IN-36).
     */
    @ExceptionHandler(ServiceTokenException.class)
    public ResponseEntity<ErrorResponse> handleServiceToken(ServiceTokenException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }
    /** Validacion de campos de la peticion (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Red de seguridad (IN-36): cualquier excepcion que no tenga un handler mas
     * especifico cae aqui en vez de escapar sin traducir hacia el manejo de
     * errores por defecto de Spring. Spring elige siempre el handler mas
     * concreto disponible, asi que este solo se activa cuando de verdad no hay
     * nada mas especifico (bugs, fallos de infraestructura no anticipados...).
     *
     * <p>El detalle completo (clase, mensaje, stack trace) se registra en el log
     * del servidor para depurar; al cliente solo le llega un mensaje generico y
     * seguro, nunca la excepcion real. Es lo que garantiza que el navegador
     * jamas vea una respuesta sin traducir (texto plano / stack trace crudo).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Excepcion no controlada en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado", request);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                      String message,
                                                      HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.name(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
