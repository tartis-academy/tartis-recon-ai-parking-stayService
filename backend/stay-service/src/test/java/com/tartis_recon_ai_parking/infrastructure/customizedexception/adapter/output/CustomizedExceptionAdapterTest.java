package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output;

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
}
