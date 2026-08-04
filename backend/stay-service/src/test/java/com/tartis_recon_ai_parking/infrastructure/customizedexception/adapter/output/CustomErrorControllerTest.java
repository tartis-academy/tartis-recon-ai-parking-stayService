package com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output;

import com.tartis_recon_ai_parking.infrastructure.customizedexception.adapter.output.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El {@code @RestControllerAdvice} no ve las excepciones de los filtros ni los
 * {@code Error}: esas acaban en {@code /error} y este controlador tiene que
 * mantener el contrato del {@code ErrorResponse} (escenarios de ruptura BD).
 */
class CustomErrorControllerTest {

    private final CustomErrorController controller = new CustomErrorController();

    @Test
    @DisplayName("/error con 500 -> ErrorResponse generico y sin detalle interno")
    void error_fiveHundred_returnsGenericErrorResponse() {
        HttpServletRequest request = request(500, "/v1/stays/check-in",
                new RuntimeException("FATAL: relation public.stays does not exist"));

        ResponseEntity<ErrorResponse> response = controller.error(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Ha ocurrido un error inesperado", response.getBody().message());
        assertFalse(flat(response).contains("relation"));
        assertFalse(flat(response).contains("Exception"));
    }

    @Test
    @DisplayName("/error con 404 -> mantiene el codigo y devuelve el path original")
    void error_notFound_keepsStatusAndPath() {
        HttpServletRequest request = request(404, "/v1/stays/no-es-un-uuid", null);

        ResponseEntity<ErrorResponse> response = controller.error(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
        assertEquals("/v1/stays/no-es-un-uuid", response.getBody().path());
        assertEquals(HttpStatus.NOT_FOUND.name(), response.getBody().error());
    }

    @Test
    @DisplayName("/error sin atributos -> 500 (fallback seguro, nunca null)")
    void error_withoutAttributes_returns500Fallback() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/error");

        ResponseEntity<ErrorResponse> response = controller.error(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().status());
    }

    private static HttpServletRequest request(int status, String path, Throwable error) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(status);
        when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(path);
        when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(error);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    private static String flat(ResponseEntity<ErrorResponse> response) {
        ErrorResponse body = response.getBody();
        return String.join(" ", body.message(), body.error(), body.path()).toLowerCase();
    }
}
