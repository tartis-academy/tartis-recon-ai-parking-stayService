# tartis-recon-ai-parking

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

Los navegadores no pueden mandar cabecera `Authorization` en `EventSource` (API nativa de JavaScript),
así que el JWT también se acepta por parámetro de query mediante `jwt` (`?jwt=<token>`) o `access_token` (`?access_token=<token>`).

### Riesgo asumido y mitigación (SSE-08 / GW-06)
- **Riesgo asumido:** Transmitir tokens en la URL expone el JWT a ser registrado en el historial del navegador o en proxies intermedios.
- **Mitigación aplicada:** Se restringe la aceptación del token por URL **exclusivamente** al método `GET` en la ruta `/v1/events` (`SecurityConfig.java`). Adicionalmente, `RequestLoggingFilter` omite la query string de los logs de acceso en el backend, y el logger de Kong redacta la URL para impedir filtraciones de tokens en logs.