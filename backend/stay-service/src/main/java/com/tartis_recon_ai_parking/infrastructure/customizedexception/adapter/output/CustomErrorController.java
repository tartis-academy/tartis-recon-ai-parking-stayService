package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output;

import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Unico punto que traduce al contrato {@link ErrorResponse} los fallos que el
 * {@code @RestControllerAdvice} no puede ver (escenarios de ruptura BD).
 *
 * <p>Cuando una excepcion se lanza dentro de un {@code Filter} (CorrelationId,
 * RequestIdentity, RequestLogging) o es un {@code Error} (OutOfMemoryError,
 * StackOverflowError), el {@code DispatcherServlet} no esta involucrado y el
 * advice de {@link CustomizedExceptionAdapter} no aplica. El contenedor reenvia
 * la peticion a {@code /error}, que sin este controlador devuelve el cuerpo por
 * defecto de Spring: un JSON distinto del contrato del {@code openapi.yml}.
 *
 * <p>Este controlador reconstruye el {@link ErrorResponse} a partir de los
 * atributos de la peticion de error y mantiene el contrato en esas rutas que se
 * salvan del advice. El detalle interno (clase, mensaje, stack trace) se
 * registra en el log del servidor; al cliente solo llega el mensaje generico
 * (IN-36).
 */
@RestController
public class CustomErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(CustomErrorController.class);

    @RequestMapping(value = "/error", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Integer rawStatus = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = HttpStatus.resolve(rawStatus == null ? 500 : rawStatus);

        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        if (status.is5xxServerError()) {
            Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            if (error instanceof Throwable t) {
                log.error("Fallo no controlado por el advice en {} (via /error)", path, t);
            } else {
                log.error("Fallo no controlado por el advice en {} (via /error)", path);
            }
        }

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.name(),
                status.is5xxServerError()
                        ? "Ha ocurrido un error inesperado"
                        : "La peticion no pudo completarse",
                path == null ? request.getRequestURI() : path);
        return ResponseEntity.status(status).body(body);
    }
}
