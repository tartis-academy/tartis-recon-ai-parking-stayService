# ADR-0001: `GET /v1/events` (SSE) vive en stay-service

## Estado

Aceptada — 2026-08-03

## Contexto

Fase II requiere notificaciones en tiempo real hacia el frontend (entrada de
vehículo, salida, cambio de tarifa, cambio de estado de plaza) vía
Server-Sent Events. No existe un `notification-service` y no conviene crear
un sexto microservicio a estas alturas del programa (bootcamp de 4 semanas).

Candidatos evaluados: cada microservicio productor expone su propio stream,
o un único endpoint centralizado.

## Decisión

`stay-service` expone `GET /v1/events`. Es el orquestador del flujo
check-in/check-out y el único servicio que ya construye y publica
`StayClosedEvent` (a RabbitMQ, ver `StayEventPublisher`/`ASY-03`). El
endpoint SSE reenvía ese mismo evento como `stay_updated` a los clientes
conectados (`SseEmitterRegistry`, SSE-02); no agrega eventos publicados por
otros servicios ni crea un `notification-service` centralizado.

Se descarta que cada servicio productor exponga su propio stream: obligaría
al frontend a abrir N conexiones `EventSource` y a Kong a enrutar N rutas
`text/event-stream`, sin beneficio real dado que hoy solo hay un evento de
dominio.

## Consecuencias

- **stay-service pasa a ser un servicio con estado en memoria.** El registro
  de emitters activos (`SseEmitterRegistry`) vive en el heap de la instancia
  que atendió la conexión `GET /v1/events`. Esto condiciona cualquier
  escalado horizontal futuro: con más de una réplica, un `StayClosedEvent`
  publicado por la réplica A nunca llegará a un cliente conectado a la
  réplica B. Antes de escalar `stay-service` a >1 réplica hace falta
  sticky sessions en el balanceador/Kong, o sustituir el registro en memoria
  por un fan-out compartido (p. ej. Redis pub/sub) que reenvíe el evento a
  todas las instancias.
- El broadcast no filtra por usuario: cualquier cliente conectado recibe
  todos los `stay_updated`, incluyendo matrícula y horarios de estancias
  ajenas. Por eso el endpoint está restringido a `ADMIN`/`OPERARIO`
  (dashboard operativo), no a `USER`. Filtrar el stream por usuario queda
  fuera de alcance.
- Alcance limitado al evento que ya existe: `entrada de vehículo`, `cambio de
  tarifa` y `cambio de estado de plaza` (mencionados en
  `FASE_II_requisitos.md`) no se emiten todavía porque stay-service no los
  publica ni los consume hoy — requerirían nuevos tipos de evento y, para los
  dos últimos, un listener de RabbitMQ hacia spot-service/tariff-service.
  Queda como trabajo futuro, no como parte de SSE-01/SSE-02.
- El token viaja como `access_token` en query param porque `EventSource`
  nativo no permite cabeceras custom (`Authorization`). Ya venía preparado
  por GW-06 (`SecurityConfig.SSE_PATH`, `bearerTokenResolver` acepta
  query param solo en esa ruta) antes de que este ADR se escribiera.
  Implicación de seguridad conocida y aceptada: el token queda en logs de
  acceso e historial del navegador con más facilidad que en una cabecera;
  se mitiga acortando el timeout del emitter.
- `publish()` y `heartbeat()` son síncronos: `publish()` corre en el hilo del
  servlet que atiende `POST /v1/stays/check-out` y `heartbeat()` en el pool de
  `@Scheduled`, y `emitter.send()` bloquea en el socket de cada cliente. Un
  cliente lento añade latencia al check-out y retrasa el heartbeat del resto.
  Se acepta para este alcance (subida a `spring.task.scheduling.pool.size=2`
  como mitigación mínima); un `@Async` con executor propio implicaría propagar
  MDC/correlation-id, ver `BeanConfiguration`.

`EventStreamRestAdapter` (adaptador de entrada) inyecta `SseEmitterRegistry`
por tipo concreto para llamar a `subscribe()`, que no forma parte de ningún
puerto — el registro solo implementa el puerto de salida
`StayEventStreamPublisher` (`publish()`). Rompe la convención hexagonal del
repo (los adaptadores de entrada solo deberían depender de puertos/casos de
uso) y es un atajo deliberado: con un único llamador, crear un puerto de
entrada solo para `subscribe()` sería sobreingeniería. Si aparece un segundo
consumidor, extraer el puerto.

## Nota de implementación

Esta rama parte de `release123`, no de `release`: es la que ya tiene el
trabajo previo de GW-06 (`SecurityConfig.SSE_PATH`, exclusión de `/v1/events`
en `RequestLoggingFilter`) y RES-03/SEC-11. `EventStreamRestAdapter` reutiliza
la constante `SecurityConfig.SSE_PATH` en vez de repetir el literal.
