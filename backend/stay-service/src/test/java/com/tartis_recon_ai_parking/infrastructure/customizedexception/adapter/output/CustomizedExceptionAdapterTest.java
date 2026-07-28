package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output;

import com.tartis_recon_ai_parking.domain.stay.exception.NoActiveTariffException;
import com.tartis_recon_ai_parking.domain.stay.exception.SpotServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.TariffServiceException;
import com.tartis_recon_ai_parking.domain.stay.exception.TicketServiceException;
import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre el unico handler de {@link CustomizedExceptionAdapter} que no se
 * ejercita a traves de MockMvc en los tests de los adaptadores REST: la
 * traduccion de errores de validacion (@Valid) a 400 (IN-36).
 */
@ExtendWith(MockitoExtension.class)
class CustomizedExceptionAdapterTest {

    private final CustomizedExceptionAdapter adapter = new CustomizedExceptionAdapter();

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("handleValidation: junta los errores de campo y devuelve 400")
    void handleValidation_buildsBadRequestWithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError plateError = new FieldError("stayRequest", "plate", "no debe estar vacio");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(plateError));
        when(request.getRequestURI()).thenReturn("/v1/stays/check-in");

        ResponseEntity<ErrorResponse> response = adapter.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("plate: no debe estar vacio", response.getBody().message());
        assertEquals("/v1/stays/check-in", response.getBody().path());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status());
    }

    @Test
    @DisplayName("handleValidation: junta varios errores de campo separados por punto y coma")
    void handleValidation_joinsMultipleFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError plateError = new FieldError("stayRequest", "plate", "no debe estar vacio");
        FieldError typeError = new FieldError("stayRequest", "vehicleType", "no reconocido");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(plateError, typeError));
        when(request.getRequestURI()).thenReturn("/v1/stays/check-in");

        ResponseEntity<ErrorResponse> response = adapter.handleValidation(ex, request);

        assertEquals("plate: no debe estar vacio; vehicleType: no reconocido", response.getBody().message());
    }

    @Test
    @DisplayName("handleSpotServiceUnavailable: traduce el fallo de spot-service a 503 con mensaje interpretable")
    void handleSpotServiceUnavailable_buildsServiceUnavailable() {
        SpotServiceException ex = new SpotServiceException(
                "No se pudo contactar con spot-service para ocupar una plaza de tipo CAR",
                new IllegalStateException("Connection refused"));
        when(request.getRequestURI()).thenReturn("/v1/stays/check-in");

        ResponseEntity<ErrorResponse> response = adapter.handleSpotServiceUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("No se pudo contactar con spot-service para ocupar una plaza de tipo CAR",
                response.getBody().message());
        assertEquals("/v1/stays/check-in", response.getBody().path());
    }

    @Test
    @DisplayName("handleNoActiveTariff: sin tarifa activa (IN-08) devuelve 409")
    void handleNoActiveTariff_buildsConflict() {
        NoActiveTariffException ex = new NoActiveTariffException("No hay tarifa activa configurada para CAR");
        when(request.getRequestURI()).thenReturn("/v1/stays/check-in");

        ResponseEntity<ErrorResponse> response = adapter.handleNoActiveTariff(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("No hay tarifa activa configurada para CAR", response.getBody().message());
    }

    @Test
    @DisplayName("handleTariffServiceUnavailable: traduce el fallo de tariff-service a 503 con mensaje interpretable")
    void handleTariffServiceUnavailable_buildsServiceUnavailable() {
        TariffServiceException ex = new TariffServiceException(
                "No se pudo contactar con tariff-service para calcular el importe de CAR",
                new IllegalStateException("Connection refused"));
        when(request.getRequestURI()).thenReturn("/v1/stays/check-out");

        ResponseEntity<ErrorResponse> response = adapter.handleTariffServiceUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("No se pudo contactar con tariff-service para calcular el importe de CAR",
                response.getBody().message());
        assertEquals("/v1/stays/check-out", response.getBody().path());
    }

    @Test
    @DisplayName("handleTicketServiceUnavailable: traduce el fallo de ticket-service a 503 con mensaje interpretable")
    void handleTicketServiceUnavailable_buildsServiceUnavailable() {
        TicketServiceException ex = new TicketServiceException(
                "No se pudo contactar con ticket-service para emitir el ticket de entrada de la estancia X",
                new IllegalStateException("Connection refused"));
        when(request.getRequestURI()).thenReturn("/v1/stays/check-in");

        ResponseEntity<ErrorResponse> response = adapter.handleTicketServiceUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("No se pudo contactar con ticket-service para emitir el ticket de entrada de la estancia X",
                response.getBody().message());
        assertEquals("/v1/stays/check-in", response.getBody().path());
    }

    @Test
    @DisplayName("handleUnexpected: red de seguridad, cualquier excepcion no anticipada da 500 con mensaje generico y seguro")
    void handleUnexpected_buildsInternalServerErrorWithSafeMessage() {
        NullPointerException ex = new NullPointerException("detalle interno que no debe llegar al cliente");
        when(request.getRequestURI()).thenReturn("/v1/stays/check-in");

        ResponseEntity<ErrorResponse> response = adapter.handleUnexpected(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        // El mensaje de la excepcion real (que podria filtrar detalles internos)
        // nunca debe llegar al cliente: solo el generico.
        assertEquals("Ha ocurrido un error inesperado", response.getBody().message());
        assertEquals("/v1/stays/check-in", response.getBody().path());
    }
}
