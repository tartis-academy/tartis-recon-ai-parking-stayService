# tartis-recon-ai-parking — stay-service

## Responsabilidad del microservicio

`stay-service` es el microservicio orquestador central del sistema de parking. Se encarga de gestionar el ciclo de vida completo de las estancias de los vehículos:
- **Entrada de Vehículo (`Check-in`):**
  - Verifica la disponibilidad de plazas por categoría (**RN-01**).
  - Consulta síncronamente a `vehicle-service` para verificar que el vehículo existe y está activo (**RN-11**).
  - Asigna plaza atómicamente a través de `spot-service` (**RN-05**).
  - Registra la estancia en estado `IN_PROGRESS` y emite el ticket de entrada.
- **Salida de Vehículo (`Check-out`):**
  - Recupera la estancia activa por matrícula o ticket de entrada.
  - Llama síncronamente a `tariff-service` para calcular el importe exacto (**RN-06** a **RN-09**).
  - Cierra la estancia pasando su estado a `FINISHED` (estado terminal **IN-19**).
  - **Publicación Asíncrona (RabbitMQ):** Publica el evento de dominio `StayClosedEvent` hacia RabbitMQ para que `ticket-service` (generación de ticket de cobro) y `spot-service` (liberación de la plaza) procesen la salida en segundo plano.
  - **Difusión en Tiempo Real (SSE):** Transmite la actualización de la estancia mediante Server-Sent Events (`GET /v1/events`) a los clientes frontend suscritos.
- **Cancelación de Estancia (`Cancel`):**
  - Soporta la cancelación manual de estancias (**RN-02**, CB06) para vehículos que retroceden antes de cruzar la barrera de entrada, pasando el estado a `CANCELLED` (estado terminal **IN-19**) y liberando la plaza reservada.

## Endpoints expuestos

Todos los endpoints requieren autenticación mediante Bearer Token (Access Token emitido por Keycloak), exceptuando la conexión SSE (que permite también el parámetro de consulta `?access_token=`) y el probe público de salud.

| Método | Endpoint | Descripción | Roles Autorizados |
|---|---|---|---|
| `POST` | `/v1/stays/check-in` | Registra la entrada de un vehículo (`IN_PROGRESS`), asigna plaza y emite ticket | `ADMIN`, `OPERARIO` |
| `POST` | `/v1/stays/check-out` | Cierra una estancia (`FINISHED`), calcula importe y publica `StayClosedEvent` | `ADMIN`, `OPERARIO` |
| `POST` | `/v1/stays/{stayId}/cancel` | Cancela una estancia que no llegó a entrar (CB06, `CANCELLED`) | `ADMIN` |
| `GET` | `/v1/stays/{stayId}` | Obtiene el detalle completo de una estancia por su UUID | `ADMIN`, `OPERARIO` |
| `GET` | `/v1/stays` | Listado paginado de estancias (filtros por `status` y `plate`) | `ADMIN`, `OPERARIO` |
| `GET` | `/v1/events` | Stream de eventos en tiempo real SSE (`text/event-stream`, `event:stay_updated`) | `ADMIN`, `OPERARIO` |
| `GET` | `/actuator/health` | Probes de salud del servicio (Liveness / Readiness) | Público |

## Eventos publicados y consumidos

Este microservicio actúa como emisor principal de eventos de dominio en la arquitectura del sistema.

- **Eventos publicados en RabbitMQ:**
  - **`StayClosedEvent`:** Publicado en el Exchange `stay.events` con routing key `stay.closed` tras completar un check-out. El mensaje incluye `eventId`, `occurredAt`, `stayId`, `spotId`, `plate`, `entryDate`, `exitDate` y `totalAmount`. Es consumido por `ticket-service` (generación de ticket) y `spot-service` (liberación de plaza).
- **Eventos consumidos de RabbitMQ:** Ninguno.

## Variables de entorno

| Variable | Descripción | Valor por defecto (Dev) | Perfil / Uso |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring Boot | `dev` | `dev` / `prod` |
| `DB_HOST` | Host de la BD compartida de desarrollo | `localhost` | Dev |
| `DB_PORT` | Puerto de la BD compartida | `5432` | Dev |
| `DB_NAME` | Nombre de la BD de desarrollo | `parking_dev` | Dev |
| `DB_USER` | Usuario de la BD de desarrollo | `parking_dev` | Dev |
| `DB_PASSWORD` | Contraseña de la BD de desarrollo | `change.me` | Dev |
| `STAY_DB_HOST` | Host de la BD dedicada de estancias | — | Prod / Aislado |
| `STAY_DB_PORT` | Puerto de la BD dedicada de estancias | `5437` / `5432` | Prod / Aislado |
| `STAY_DB_NAME` | Nombre de la BD dedicada | `stay_db` | Prod / Aislado |
| `STAY_DB_USER` | Usuario de la BD dedicada | — | Prod / Aislado |
| `STAY_DB_PASSWORD` | Contraseña de la BD dedicada | — | Prod / Aislado |
| `RABBITMQ_HOST` | Host del broker RabbitMQ | `rabbitmq` | Dev / Prod |
| `RABBITMQ_USER` | Usuario de autenticación RabbitMQ | `guest` | Dev / Prod |
| `RABBITMQ_PASSWORD` | Contraseña de autenticación RabbitMQ | `guest` | Dev / Prod |
| `KEYCLOAK_ISSUER_URI` | URI del emisor de Keycloak (Issuer URI) | `http://localhost:8180/realms/parking` | Dev / Prod |
| `VEHICLE_SERVICE_URL` | URL base del microservicio `vehicle-service` | `http://localhost:8081` | Dev / Prod |
| `SPOT_SERVICE_URL` | URL base del microservicio `spot-service` | `http://localhost:8082` | Dev / Prod |
| `TARIFF_SERVICE_URL` | URL base del microservicio `tariff-service` | `http://localhost:8083` | Dev / Prod |
| `TICKET_SERVICE_URL` | URL base del microservicio `ticket-service` | `http://localhost:8084` | Dev / Prod |

## Ejecución de forma aislada

Para ejecutar y probar `stay-service` de forma independiente sin depender del resto de microservicios:

1. **Opción 1: Entorno de Desarrollo (Perfil `dev`)**
   Navegar a la carpeta del microservicio y arrancar con Maven:
   ```bash
   cd backend/stay-service
   mvn spring-boot:run
   ```
   *El servicio se conectará al esquema `stay` del Postgres compartido.*

2. **Opción 2: Base de Datos Dedicada (Perfil `prod` / Contenedores Aislados)**
   Para ejecutar contra una base de datos PostgreSQL exclusiva en puerto `5437`:
   ```bash
   cd backend/stay-service
   cp .env.example .env
   docker compose up -d
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

## Migraciones de base de datos (Flyway)

El esquema ya no se crea a mano ni con un `schema.sql` montado como init
script: `V1__init.sql` (en `backend/stay-service/src/main/resources/db/migration`)
es la baseline, y Flyway la aplica solo al arrancar la app contra la BD
dedicada (perfil `prod`). En dev, Flyway está desactivado
(`spring.flyway.enabled=false` en `application-dev.properties`): el Postgres
compartido con 5 schemas sigue gestionado por `ddl-auto=update`, fuera del
alcance de esta migración.

Para añadir un cambio de esquema: crea `V2__descripcion.sql` (nunca edites
`V1__init.sql` una vez desplegado) en la misma carpeta, con el DDL nuevo.
Flyway lo detecta y lo aplica en el siguiente arranque.

## Escaneo de imagen (Trivy)

El job `docker-scan` de la CI construye la imagen final del Dockerfile y la
escanea con [Trivy](https://trivy.dev/). El informe completo (`CRITICAL` +
`HIGH`) se publica siempre en la pestaña **Security** del repo; solo una
vulnerabilidad `CRITICAL` hace fallar el job.

Si una `CRITICAL` no tiene fix disponible todavía y hay que aceptar el riesgo
de forma consciente, se ignora explícitamente añadiendo su CVE a un
`.trivyignore` en la raíz del repo (no existe ninguno hoy).

## Eventos en tiempo real (SSE)

`GET /v1/events` (rol `ADMIN` u `OPERARIO`) abre una conexión
`text/event-stream` de larga duración. Al conectar, el servidor manda
`event:connected` con `data:ok`, y cada pocos segundos una línea de
comentario `:heartbeat` para que proxies intermedios no corten la conexión
por inactividad.

El único evento de dominio hoy es `event:stay_updated`, emitido al cerrar
una estancia (check-out). Trae:
- `id:` — mismo valor que el `eventId` del payload; el navegador lo usa para
  rellenar `Last-Event-ID` si reconecta. No hay un log de eventos que
  reproducir: un cliente que reconecta solo recibe eventos nuevos, no
  recupera lo que se perdió mientras estaba desconectado.
- `data:` — JSON de `StayClosedEvent` (`eventId`, `type`, `version`,
  `occurredAt`, `data` con `stayId`, `spotId`, `plate`, `entryDate`,
  `exitDate`, `totalAmount`).

Los navegadores no pueden mandar cabecera `Authorization` en `EventSource`,
así que el JWT también se acepta como query param `access_token` — y solo con ese
nombre, el del RFC 6750. Es el que manda el frontend (`use-sse.ts`) y el que declara
la route `stay-service-events-route` de `kong/kong.yml`. `?jwt=` (el default del
plugin `jwt` de Kong) no autentica: un segundo nombre obliga a reimplementar a mano
la detección de token smuggling que Spring ya trae para `access_token`.

Detalle completo (contrato, roles, límites) en `openapi.yml` (path
`/events`) y en `docs/adr/0001-sse-endpoint-en-stay-service.md`.
