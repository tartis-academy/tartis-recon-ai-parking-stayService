# stay-service — Alcance y Especificación de Fase 1 (v0.5.0)

Este documento especifica el alcance funcional, el modelo de datos y los endpoints del microservicio `stay-service` correspondientes a la **Fase 1 (MVP - v0.5.0)** del sistema **TARTIS Recon-AI**.

---

## 1. Responsabilidad del Microservicio en Fase 1

En la Fase 1 (`v0.5.0`), `stay-service` actúa como el **orquestador síncrono inicial del ciclo de vida de estancias**:

- **Check-in Síncrono:** Registro de entrada verificando la existencia del vehículo en `vehicle-service` y reservando plaza libre en `spot-service`.
- **Check-out Síncrono:** Cierre de estancia calculando el importe consumido con `tariff-service` y liberando la plaza de forma síncrona.
- **Consulta de Estancias:** Búsqueda paginada y consulta por UUID.

---

## 2. Modelo de Dominio (`Stay`) en Fase 1

| Atributo | Tipo Java | Descripción | Obligatorio |
|---|---|---|---|
| `id` | `UUID` | Identificador único de la estancia | Sí |
| `vehicleId` | `UUID` | ID del vehículo registrado | Sí |
| `spotId` | `UUID` | ID de la plaza asignada | Sí |
| `checkInAt` | `LocalDateTime` | Fecha y hora de entrada en barrera | Sí |
| `checkOutAt` | `LocalDateTime` | Fecha y hora de salida | No |
| `totalAmount` | `BigDecimal` | Importe total calculated | No |
| `status` | `StayStatus` | Estado (`ACTIVE`, `FINISHED`) | Sí |

---

## 3. Endpoints REST Expuestos en Fase 1 (v0.5.0)

| Método HTTP | Endpoint | Descripción | Respuesta Exitosa |
|---|---|---|---|
| `POST` | `/v1/stays/check-in` | Registro síncrono de entrada (RN-01, RN-05) | `201 Created` (`StayResponse`) |
| `POST` | `/v1/stays/check-out` | Cierre síncrono de estancia | `200 OK` (`StayResponse`) |
| `GET` | `/v1/stays/{stayId}` | Consulta de estancia por UUID | `200 OK` (`StayResponse`) |
| `GET` | `/v1/stays` | Listado paginado de estancias | `200 OK` (`Page<StayResponse>`) |

---

## 4. Persistencia PostgreSQL — Baseline (`V1__init.sql`)

```sql
CREATE SCHEMA IF NOT EXISTS stay;

CREATE TABLE stay.stays (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL,
    spot_id UUID NOT NULL,
    check_in_at TIMESTAMP NOT NULL,
    check_out_at TIMESTAMP,
    total_amount NUMERIC(10,2),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 5. Exclusiones de la Fase 1 (Diferencias con Fase 2 / v1.0.0)

- ❌ **Sin publicación asíncrona de eventos:** En Fase 1 no existía `StayEventPublisherAdapter` ni el envío de `StayClosedEvent` a RabbitMQ.
- ❌ **Sin Server-Sent Events (SSE):** Sin endpoint `GET /v1/events` ni transmisión en tiempo real hacia el Frontend.
- ❌ **Sin cancelación manual de estancia (CB06 / RN-02):** Sin endpoint `POST /v1/stays/{stayId}/cancel` ni estado `CANCELLED` (**IN-19**).
- ❌ **Sin resiliencia Resilience4j:** Sin timeouts ni fallbacks configurados en clientes REST.
- ❌ **Sin autenticación Keycloak ni RBAC (SEC-03).**
- ❌ **Sin Kong API Gateway.**
