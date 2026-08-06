# Changelog

All notable changes to the `stay-service` microservice will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-08-04

### Added
- **Publicación Asíncrona de Eventos (`StayClosedEvent`):** Implementado `StayEventPublisherAdapter` enviando eventos de dominio al Exchange `stay.events` (routing key `stay.closed`) de RabbitMQ tras cada check-out para la generación de ticket y liberación de plaza en segundo plano.
- **Notificaciones en Tiempo Real mediante Server-Sent Events (SSE):** Implementado endpoint `GET /v1/events` (`EventStreamRestAdapter` y `SseEmitterRegistry`) emitiendo `event:connected`, pings periódicos `:heartbeat` y `event:stay_updated` con payload `StayClosedEvent`.
- **Autenticación SSE por Query Parameter (RFC 6750):** Soporte para autenticación `?access_token=` en `GET /v1/events` para compatibilidad con el objeto nativo `EventSource` de los navegadores.
- **Resiliencia & Fallbacks (Resilience4j):** Configurados timeouts y mecanismos de resiliencia/fallback en los clientes HTTP (`StaySpotClientAdapter`, `StayVehicleClientAdapter`, `StayTariffClientAdapter`) garantizando que caídas secundarias no impidan completar operaciones críticas.
- **Cancelación Manual de Estancia (CB06 / RN-02):** Implementado endpoint `POST /v1/stays/{stayId}/cancel` para estancias que retroceden antes de entrar a la barrera, liberando la plaza y pasando la estancia a estado terminal `CANCELLED` (IN-19).
- **Protección contra Condiciones de Carrera (IN-02 / CB05):** Control de concurrencia para evitar dos check-ins simultáneos con la misma matrícula o estancia duplicada (retorna HTTP 409).
- **Integración con Keycloak & Spring Security:** OAuth2 Resource Server para validación de Bearer Access Tokens emitidos por Keycloak.
- **Enrutamiento por API Gateway (Kong):** Enrutamiento centralizado y validación de tokens JWT en el perímetro.
- **Trazabilidad Distribuida & Logging (GW-06):** Inclusión de `CorrelationIdFilter`, `RequestIdentityFilter` y `RequestLoggingFilter` inyectando `correlationId`, `userName` y `clientId` en el MDC.

### Changed
- **Formato Común de Errores (SEC-11 / RFC 7807):** Estandarización de respuestas mediante `CustomizedExceptionAdapter` y `CustomErrorController` devolviendo `ProblemDetail` / `ErrorResponse` uniforme.
- **Control de Acceso basado en Roles (RBAC):** Restricción de endpoints según matriz `SEC-03` (`ADMIN` para cancelación; `ADMIN`/`OPERARIO` para check-in, check-out, consultas y stream SSE).
- **Base de Datos Dedicada:** Perfil `prod` con PostgreSQL dedicada en puerto 5437.

### Fixed
- **Publicación Silenciosa de Eventos:** Asegurado que un fallo puntual al publicar en RabbitMQ o reenviar por SSE no revierta (rollback) la transacción de check-out en la base de datos (se registra en log para revisión manual).
- **Manejo de Respuestas de Autenticación (401 / 403):** Emisión del encabezado `WWW-Authenticate` en respuestas 401.

### Security
- **Protección con `@PreAuthorize`:** Control de acceso en adaptadores REST.
- **Escaneo Continuo de Vulnerabilidades:** Pipeline CI/CD integrado con Trivy (`docker-scan`).

## [0.5.0] - 2026-07-25

### Added
- **MVP Inicial de `stay-service`:** Implementación inicial de la arquitectura hexagonal para la orquestación del parking.
- **Endpoints REST Síncronos:**
  - `POST /v1/stays/check-in`: Registro de entrada de vehículo (RN-01, RN-05, RN-11).
  - `POST /v1/stays/check-out`: Cierre de estancia y cálculo de importe síncrono.
  - `GET /v1/stays/{stayId}`: Consulta de estancia por UUID.
  - `GET /v1/stays`: Listado paginado de estancias.
- **Persistencia PostgreSQL:** Configuración JPA con esquema `stay`.
- **Contrato OpenAPI:** Especificación en `openapi.yml`.

[1.0.0]: https://github.com/tartis-academy/tartis-recon-ai-parking-stayService/compare/v0.5.0...v1.0.0
[0.5.0]: https://github.com/tartis-academy/tartis-recon-ai-parking-stayService/releases/tag/v0.5.0
