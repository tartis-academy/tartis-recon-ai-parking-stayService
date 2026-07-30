package com.tartis_recon_ai_parking.domain.stay.exception;

/**
 * Se lanza cuando stay-service no puede obtener el token de servicio con el
 * que llama a los demas microservicios: Keycloak caido, credenciales de
 * cliente mal configuradas, o el cliente no existe en el realm.
 *
 * <p>Es un fallo de integracion igual que {@code VehicleServiceException} y
 * companeros, y se traduce a 503 por el mismo motivo (IN-36): sin esto salia
 * como {@code IllegalStateException} y caia en el handler generico, que
 * responde "Ha ocurrido un error inesperado" y deja el mensaje util solo en el
 * log — justo el tipo de diagnostico opaco que el resto de esta clase de
 * excepciones existe para evitar.
 *
 * <p>La lanza el interceptor de {@code BeanConfiguration}, antes de que la
 * peticion llegue a salir sin cabecera {@code Authorization}.
 */
public class ServiceTokenException extends RuntimeException {

    public ServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceTokenException(String message) {
        super(message);
    }
}
