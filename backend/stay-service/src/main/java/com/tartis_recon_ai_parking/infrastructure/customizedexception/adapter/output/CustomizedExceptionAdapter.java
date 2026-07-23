package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output;

import com.tartis_recon_ai_parking.domain.stay.exception.DuplicateActiveStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.InvalidStayException;
import com.tartis_recon_ai_parking.domain.stay.exception.NoAvailableSpotException;
import com.tartis_recon_ai_parking.domain.stay.exception.StayNotFoundException;
import com.tartis_recon_ai_parking.domain.stay.exception.VehicleDeactivatedException;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Unico punto que traduce excepciones de dominio/aplicacion a respuestas HTTP
 * (invariante IN-36). Ningun caso de uso ni entidad conoce codigos de estado.
 *
 * <p>Codigos del check-in ({@code POST /v1/stays/check-in}), segun el openapi.yml:
 * <ul>
 *   <li><b>400</b> — matricula vacia / tipo de vehiculo invalido / validacion de campos</li>
 *   <li><b>404</b> — estancia inexistente (consultas)</li>
 *   <li><b>409</b> — parking completo (RN-01) o vehiculo ya dentro (IN-02, CB-05)</li>
 *   <li><b>422</b> — vehiculo dado de baja (RN-11)</li>
 * </ul>
 *
 * <p>Los tres casos de denegacion (409, 422) comparten consecuencia fisica: la
 * <b>barrera permanece cerrada</b> y no se crea ninguna estancia.
 */
@RestControllerAdvice
public class CustomizedExceptionAdapter {

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

    /** Validacion de campos de la peticion (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
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
