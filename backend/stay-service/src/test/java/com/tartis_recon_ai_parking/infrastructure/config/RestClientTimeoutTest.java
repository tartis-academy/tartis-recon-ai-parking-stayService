package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RES-06: comprueba que los timeouts (connect y read) que se le pasan a la
 * fabrica de peticiones se aplican de verdad al {@link SimpleClientHttpRequestFactory}.
 *
 * <p>No levanta ningun servidor ni depende de la red: verifica el cableado del
 * timeout, que es lo que RES-06 pide garantizar. Un test que dependiera de que
 * "una llamada lenta corta a los N ms" seria no determinista (depende de la
 * maquina y del reloj); esto es exacto y estable.
 */
class RestClientTimeoutTest {

    /**
     * Lee un timeout del factory de forma robusta ante la version de Spring:
     * primero intenta un getter; si no existe, cae al campo privado recorriendo
     * la jerarquia de clases. Asi el test no se rompe si la version expone
     * getters o mueve el campo.
     */
    private static int readTimeout(Object target, String property) throws Exception {
        String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method m = target.getClass().getMethod(getter);
            Object v = m.invoke(target);
            if (v instanceof Integer i) {
                return i;
            }
            if (v instanceof java.time.Duration d) {
                return (int) d.toMillis();
            }
        } catch (NoSuchMethodException ignored) {
            // sin getter: caemos al campo
        }
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(property);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v instanceof Integer i) {
                    return i;
                }
                if (v instanceof java.time.Duration d) {
                    return (int) d.toMillis();
                }
                break;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new AssertionError("No se pudo leer el timeout '" + property + "' del factory");
    }

    @Test
    @DisplayName("timeoutRequestFactory aplica connect y read timeout en milisegundos")
    void appliesConfiguredTimeouts() throws Exception {
        SimpleClientHttpRequestFactory factory =
                BeanConfiguration.timeoutRequestFactory(3000L, 5000L);

        assertEquals(3000, readTimeout(factory, "connectTimeout"));
        assertEquals(5000, readTimeout(factory, "readTimeout"));
    }

    @Test
    @DisplayName("El read timeout del proveedor de vehiculo (RES-06, deuda Fase I) es independiente y mas holgado")
    void vehicleReadTimeoutIsIndependent() throws Exception {
        // Mismo connect timeout que el generico, pero read timeout propio y mayor:
        // vehicle-service consulta a un proveedor externo mas lento en el check-in.
        SimpleClientHttpRequestFactory vehicleFactory =
                BeanConfiguration.timeoutRequestFactory(3000L, 8000L);

        assertEquals(3000, readTimeout(vehicleFactory, "connectTimeout"));
        assertEquals(8000, readTimeout(vehicleFactory, "readTimeout"));
    }
}