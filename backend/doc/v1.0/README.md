# Alcance de la Fase II (v1.0.0) — stay-service

Documento explicativo del alcance, responsabilidad, modelo de dominio, endpoints expuestos, mensajería asíncrona, Server-Sent Events, seguridad e infraestructura del microservicio `stay-service` durante la **Fase II (v1.0.0)** del sistema de parking inteligente **TARTIS Recon-AI**.

---

## 1. Responsabilidad del Microservicio en Fase II

En la Fase II, `stay-service` actúa como el orquestador central enriquecido con eventos asíncronos y transmisión en tiempo real:
- **Publicación Asíncrona de Eventos (`StayClosedEvent`):** Envío de eventos de dominio al Exchange `stay.events` (routing key `stay.closed`) en RabbitMQ tras check-out para desencadenar la emisión de ticket y liberación de plaza en segundo plano.
- **Notificaciones en Tiempo Real mediante Server-Sent Events (SSE):** Endpoint `GET /v1/events` (`EventStreamRestAdapter` y `SseEmitterRegistry`) emitiendo `:heartbeat` periódicos y el evento `event:stay_updated` con payload `StayClosedEvent`.
- **Autenticación SSE por Query Parameter (RFC 6750):** Soporte para `?access_token=` en `GET /v1/events` para compatibilidad nativa con `EventSource` de los navegadores.
- **Cancelación Manual de Estancia (CB06 / RN-02):** Endpoint `POST /v1/stays/{stayId}/cancel` para vehículos que dan marcha atrás antes de la barrera, liberando la plaza y pasando la estancia a estado terminal `CANCELLED` (**IN-19**).
- **Protección contra Condiciones de Carrera (IN-02 / CB05):** Control de concurrencia para evitar dos check-ins simultáneos con la misma matrícula.
- **Resiliencia & Fallbacks (Resilience4j):** Timeouts y fallbacks en los clientes HTTP (`StaySpotClientAdapter`, `StayVehicleClientAdapter`, `StayTariffClientAdapter`).
- **Seguridad OAuth2 / Keycloak & Kong:** Resource Server para validación JWT y roles RBAC (`ADMIN`, `OPERARIO`).

---

## 2. Modelo de Dominio y Persistencia (Fase II)

Entidad **`Stay`**:

| Atributo | Tipo | Descripción | Validación / Restricción |
|---|---|---|---|
| `id` | `UUID` | Identificador único universal de la estancia | Autogenerado (PK) |
| `vehiclePlate` | `String` | Matrícula del vehículo en estacionamiento | Prevención de duplicados (**IN-02**) |
| `spotId` | `UUID` | Identificador de la plaza asignada | Relación con `spot-service` |
| `entryTime` | `LocalDateTime` | Fecha y hora de entrada al parking | No nulo |
| `exitTime` | `LocalDateTime` | Fecha y hora de salida o cancelación | Asignado en check-out/cancel |
| `calculatedAmount` | `BigDecimal` | Importe calculado por `tariff-service` | Calculado en check-out |
| `status` | `StayStatus` | Estado de la estancia (`IN_PROGRESS`, `FINISHED`, `CANCELLED` **IN-19**) | Máquina de estados estricta |

---

## 3. Endpoints Expuestos y Eventos Publicados (Fase II)

### API REST & SSE:
| Método HTTP | Endpoint | Descripción | Rol Keycloak Requerido |
|---|---|---|---|
| `POST` | `/v1/stays/check-in` | Registro de entrada de vehículo (**RN-01**, **RN-05**) | `ADMIN`, `OPERARIO` |
| `POST` | `/v1/stays/check-out` | Cierre de estancia y cálculo síncrono | `ADMIN`, `OPERARIO` |
| `POST` | `/v1/stays/{stayId}/cancel` | Cancelación manual por marcha atrás (CB06 / **RN-02**) | `ADMIN` |
| `GET` | `/v1/stays/{stayId}` | Consulta de estancia por UUID | `ADMIN`, `OPERARIO` |
| `GET` | `/v1/stays` | Listado paginado de estancias | `ADMIN`, `OPERARIO` |
| `GET` | `/v1/events` | **Stream SSE en tiempo real (`text/event-stream`)** | `ADMIN`, `OPERARIO` (`?access_token=`) |

### Publicación Asíncrona AMQP:
- **Exchange:** `stay.events` (Topic)
- **Routing Key:** `stay.closed`
- **Payload:** `StayClosedEvent` (contiene `stayId`, `vehiclePlate`, `spotId`, `totalAmount`, `closedAt`).

---

## 4. Arquitectura, Seguridad e Infraestructura (Fase II)

- **Adaptadores de Salida Duales de Eventos:**
  - `StayEventPublisherAdapter` (`RabbitTemplate`) hacia RabbitMQ.
  - `SseEmitterRegistry` hacia clientes web `EventSource`.
- **Publicación Silenciosa:** Fallo puntual en mensajería no revierte la transacción de check-out en base de datos.
- **Base de Datos:** Postgres dedicado `stay_db` en puerto `5437` (perfil `prod`), migraciones Flyway `V1__init.sql`.
- **Formato Común de Errores RFC 7807 (SEC-11):** Respuestas de error estandarizadas devolviendo `ProblemDetail` / `ErrorResponse`.
