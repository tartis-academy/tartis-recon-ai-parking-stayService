# stay-service — Alcance y Especificación de Fase 2 (v1.0.0)

Este documento especifica el alcance funcional completo, la publicación de eventos, Server-Sent Events y la seguridad del microservicio `stay-service` correspondientes a la **Fase 2 (v1.0.0)** del sistema **TARTIS Recon-AI**.

---

## 1. Responsabilidad del Microservicio en Fase 2

En la Fase 2 (`v1.0.0`), `stay-service` se consagra como el **orquestador central reactivo y asíncrono** del sistema de parking:

- **Publicación Asíncrona de `StayClosedEvent`:** Emisión de eventos de dominio a RabbitMQ (Exchange `stay.events`, routing key `stay.closed`) tras cada check-out para desencadenar en segundo plano la emisión del ticket en `ticket-service` y la liberación de la plaza en `spot-service`.
- **Notificaciones en Tiempo Real (SSE):** Endpoint `GET /v1/events` emitiendo `event:connected`, `:heartbeat` periódicos y `event:stay_updated` con el payload del evento.
- **Autenticación SSE por Query Parameter (RFC 6750):** Soporte para `?access_token=` en `GET /v1/events` para clientes nativos `EventSource` de navegador.
- **Cancelación Manual de Estancia (CB06 / RN-02):** Endpoint `POST /v1/stays/{stayId}/cancel` pasando la estancia a estado terminal `CANCELLED` (**IN-19**) y liberando la plaza reservada.
- **Protección contra Condiciones de Carrera (IN-02 / CB05):** Control de concurrencia para evitar dos check-ins simultáneos con la misma matrícula (`409 Conflict`).
- **Resiliencia & Fallbacks (Resilience4j):** Timeouts y fallbacks en clientes HTTP (`StaySpotClientAdapter`, `StayVehicleClientAdapter`, `StayTariffClientAdapter`).
- **Seguridad & RBAC (SEC-03):** OAuth2 Resource Server con Keycloak IdP y Kong API Gateway (`ADMIN` para cancelación; `ADMIN`/`OPERARIO` para check-in, check-out, consultas y SSE).

---

## 2. Endpoints REST & SSE (v1.0.0)

| Método HTTP | Endpoint | Descripción | Roles Permitidos | Respuesta Exitosa |
|---|---|---|---|---|
| `POST` | `/v1/stays/check-in` | Registro de entrada de vehículo (RN-01, RN-05) | `ADMIN`, `OPERARIO` | `201 Created` |
| `POST` | `/v1/stays/check-out` | Cierre de estancia (publica `StayClosedEvent`) | `ADMIN`, `OPERARIO` | `200 OK` |
| `POST` | `/v1/stays/{stayId}/cancel` | **Cancelación manual de estancia (CB06 / RN-02)** | `ADMIN` | `200 OK` |
| `GET` | `/v1/stays/{stayId}` | Consulta por UUID | `ADMIN`, `OPERARIO` | `200 OK` |
| `GET` | `/v1/stays` | Listado paginado de estancias | `ADMIN`, `OPERARIO` | `200 OK` |
| `GET` | `/v1/events` | **Stream SSE en tiempo real (`text/event-stream`)** | `ADMIN`, `OPERARIO` | `200 OK (Stream)` |

---

## 3. Persistencia PostgreSQL (Migraciones Flyway)

### `V1__init.sql` (Baseline) & `V2__add_cancelled_status.sql`
```sql
ALTER TABLE stay.stays ADD CONSTRAINT check_stay_status CHECK (status IN ('ACTIVE', 'FINISHED', 'CANCELLED'));
```
En entorno `prod`, se ejecuta sobre base de datos dedicada `stay_db` en puerto `5437`.
