# tartis-recon-ai-parking — stay-service

## 1. Responsabilidad del microservicio

`stay-service` es el microservicio orquestador central del sistema de parking **TARTIS Recon-AI**. Se encarga de gestionar el ciclo de vida completo de las estancias de los vehículos:

- **Entrada de Vehículo (`Check-in`):**
  - Verifica la disponibilidad de plazas por categoría (**RN-01**).
  - Consulta síncronamente a `vehicle-service` para verificar que el vehículo existe y está activo (**RN-11**).
  - Asigna plaza atómicamente a través de `spot-service` (**RN-05**).
  - Registra la estancia en estado `IN_PROGRESS` y coordina la emisión del ticket.
- **Salida de Vehículo (`Check-out`):**
  - Recupera la estancia activa por matrícula o identificador.
  - Llama síncronamente a `tariff-service` para calcular el importe exacto acumulado.
  - Cierra la estancia pasando su estado a `FINISHED` (estado terminal **IN-19**).
  - **Publicación Asíncrona (RabbitMQ ASY-01):** Publica el evento de dominio `StayClosedEvent` hacia RabbitMQ para que `ticket-service` (emisión de ticket de cobro) y `spot-service` (liberación de la plaza) procesen la salida de forma asíncrona.
  - **Difusión en Tiempo Real (SSE-01):** Transmite las actualizaciones de estancia mediante Server-Sent Events (`GET /v1/events`) a los clientes suscritos.
- **Cancelación de Estancia (`Cancel`):**
  - Soporta la cancelación manual de estancias (**RN-02**, CB06) para vehículos que retroceden antes de traspasar la barrera de entrada, pasando su estado a `CANCELLED` (estado terminal **IN-19**) y liberando la plaza reservada.

---

## 2. Endpoints expuestos

Todos los endpoints requieren autenticación perimetral mediante Bearer Access Token (emitido por Keycloak), exceptuando la conexión SSE (que permite autenticación nativa por query param `?access_token=` según RFC 6750) y las sondas públicas de salud.

| Método | Endpoint | Descripción | Roles Autorizados (RBAC SEC-03) | Respuesta Exitosa |
|---|---|---|---|---|
| `POST` | `/v1/stays/check-in` | Registra la entrada de un vehículo (`IN_PROGRESS`), asigna plaza (RN-01, RN-05) | `ADMIN`, `OPERARIO` | `201 Created` (`StayResponse`) |
| `POST` | `/v1/stays/check-out` | Cierra una estancia (`FINISHED`), calcula importe y publica `StayClosedEvent` | `ADMIN`, `OPERARIO` | `200 OK` (`StayResponse`) |
| `POST` | `/v1/stays/{stayId}/cancel` | Cancela una estancia que no llegó a entrar (CB06, estado `CANCELLED` IN-19) | `ADMIN` | `200 OK` (`StayResponse`) |
| `GET` | `/v1/stays/{stayId}` | Obtiene el detalle completo de una estancia por su UUID | `ADMIN`, `OPERARIO` | `200 OK` (`StayResponse`) |
| `GET` | `/v1/stays` | Listado paginado de estancias (filtros por `status` y `plate`) | `ADMIN`, `OPERARIO` | `200 OK` (`Page<StayResponse>`) |
| `GET` | `/v1/events` | Stream SSE en tiempo real (`text/event-stream`, `event:stay_updated`) (SSE-01) | `ADMIN`, `OPERARIO` | `200 OK` (Stream) |
| `GET` | `/actuator/health` | Probes de salud del servicio (Liveness / Readiness) | Público | `200 OK` |

---

## 3. Casos de Uso (Arquitectura Hexagonal)

Los casos de uso coordinan la interacción síncrona y asíncrona entre microservicios:

- **`CheckInUseCase`:** Verifica vehículo activo, comprueba disponibilidad de plaza, ocupa la plaza atómicamente y crea la estancia activa.
- **`CheckOutUseCase`:** Calcula importe consumido con `tariff-service`, cierra la estancia (`FINISHED`), publica el evento `StayClosedEvent` en RabbitMQ y notifica al registro SSE.
- **`CancelStayUseCase`:** Marca la estancia como `CANCELLED` (IN-19) y libera la plaza si el vehículo retrocede en barrera (RN-02).
- **`GetStayUseCase`:** Recupera una estancia por UUID.
- **`ListStaysUseCase`:** Obtiene listados paginados de estancias con filtros por estado o matrícula.

### Puertos de Dominio:
- **Puertos de Entrada:** `StayRestAdapter` (REST API), `EventStreamRestAdapter` (SSE `GET /v1/events`).
- **Puertos de Salida:**
  - `StayPersistence` (Persistencia PostgreSQL).
  - `StayVehicleClient` (Cliente REST con resiliencia Resilience4j hacia `vehicle-service`).
  - `StaySpotClient` (Cliente REST con resiliencia Resilience4j hacia `spot-service`).
  - `StayTariffClient` (Cliente REST con resiliencia Resilience4j hacia `tariff-service`).
  - `StayEventPublisher` (`StayEventPublisherAdapter` enviando mensajes a RabbitMQ).
  - `StayEventStreamPublisher` (`SseEmitterRegistry` manteniendo emisores SSE en memoria).

---

## 4. Eventos publicados y consumidos

### Eventos Publicados en RabbitMQ:
- **`StayClosedEvent`:** Publicado en el Exchange Topic `stay.events` con routing key `stay.closed` tras cada check-out. Contiene: `eventId`, `occurredAt`, `stayId`, `spotId`, `plate`, `entryDate`, `exitDate` y `totalAmount`.
- Consumido por `ticket-service` (emisión de ticket de cobro) y `spot-service` (liberación de la plaza).

### Eventos Difundidos por Server-Sent Events (SSE):
- **`event:stay_updated`:** Difundido en tiempo real en la ruta `GET /v1/events` al registrar o cerrar una estancia.

---

## 5. Variables de entorno

| Variable | Descripción | Valor por defecto (Dev) | Perfil / Uso |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring Boot | `dev` | `dev` / `prod` |
| `SERVER_PORT` | Puerto HTTP del servicio | `8085` | Dev / Prod |
| `DB_HOST` | Host de la BD compartida de desarrollo | `localhost` | Dev |
| `DB_PORT` | Puerto de la BD compartida | `5432` | Dev |
| `DB_NAME` | Nombre de la BD de desarrollo | `parking_dev` | Dev |
| `STAY_DB_HOST` | Host de la BD dedicada de estancias | `parking-stay-postgres` | Prod / Aislado |
| `STAY_DB_PORT` | Puerto del host para la BD dedicada | `5437` (externo) / `5432` (interno) | Prod / Aislado |
| `STAY_DB_NAME` | Nombre de la BD dedicada | `stay_db` | Prod / Aislado |
| `STAY_DB_USER` | Usuario de la BD dedicada | `stay_user` | Prod / Aislado |
| `STAY_DB_PASSWORD` | Contraseña de la BD dedicada | `stay_pass` | Prod / Aislado |
| `RABBITMQ_HOST` | Host del broker RabbitMQ | `rabbitmq` | Dev / Prod |
| `RABBITMQ_USER` | Usuario de autenticación RabbitMQ | `guest` | Dev / Prod |
| `RABBITMQ_PASSWORD` | Contraseña de autenticación RabbitMQ | `guest` | Dev / Prod |
| `KEYCLOAK_ISSUER_URI` | URI del emisor de Keycloak | `http://localhost:8180/realms/parking` | Dev / Prod |
| `VEHICLE_SERVICE_URL` | URL base de `vehicle-service` | `http://localhost:8081` | Dev / Prod |
| `SPOT_SERVICE_URL` | URL base de `spot-service` | `http://localhost:8082` | Dev / Prod |
| `TARIFF_SERVICE_URL` | URL base de `tariff-service` | `http://localhost:8083` | Dev / Prod |
| `TICKET_SERVICE_URL` | URL base de `ticket-service` | `http://localhost:8084` | Dev / Prod |

---

## 6. Ejecución de forma aislada

### Opción 1: Entorno de Desarrollo (Perfil `dev`)
```bash
cd backend/stay-service
mvn spring-boot:run
```

### Opción 2: Base de Datos Dedicada (Perfil `prod` / Contenedores Aislados)
1. Arrancar la base de datos exclusiva PostgreSQL en el puerto `5437`:
   ```bash
   cd backend/stay-service
   cp .env.example .env
   docker compose up -d
   ```
2. Ejecutar la aplicación Spring Boot activando el perfil `prod` para aplicar migraciones Flyway (`V1__init.sql`, `V2__add_cancelled_status.sql`):
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

---

## 7. Eventos en tiempo real (Server-Sent Events)

El endpoint `GET /v1/events` (roles `ADMIN`, `OPERARIO`) mantiene un canal de transmisión `text/event-stream`.
- **Autenticación por Query Param (RFC 6750):** `GET /v1/events?access_token=<ACCESS_TOKEN>` para clientes nativos `EventSource`.
- **Heartbeat periódicos:** Envío de comentarios `:heartbeat` para mantener activa la conexión en proxies.
