# RES-09 — Enseñar el estado de los circuit breakers en la demo

Guion para mostrar en vivo las transiciones **CLOSED → OPEN → HALF_OPEN → CLOSED**
de los circuit breakers de stay-service.

---

## Qué endpoints usar

| Endpoint | Para qué | Acceso |
|---|---|---|
| `/actuator/circuitbreakers` | Foto del estado actual de los 4 circuitos | Autenticado |
| `/actuator/circuitbreakerevents` | Histórico de transiciones (**el mejor para la demo**) | Autenticado |
| `/actuator/health` | Estado del circuito entre los demás indicadores | Autenticado **+ rol ADMIN** |

Los tres están ya expuestos vía `management.endpoints.web.exposure.include`
(RES-02). No hace falta tocar configuración.

**Usa `/actuator/circuitbreakerevents` como endpoint principal de la demo.**
`/circuitbreakers` da una foto del estado actual; `circuitbreakerevents` da la
película completa, que es literalmente lo que pide el criterio de aceptación.
El buffer está en 20 eventos (`event-consumer-buffer-size` en `application.yml`),
suficiente para un ciclo completo.

---

## Aviso importante: hace falta token

Ninguno de estos endpoints es público. En `SecurityConfig` solo
`/actuator/health/**` y `/error` son `permitAll`; todo lo demás cae en
`anyRequest().authenticated()`.

Esto es **deliberado**: `/actuator/health` es público y sus detalles incluyen el
campo `error` que `DataSourceHealthIndicator` rellena con el mensaje crudo de la
excepción de PostgreSQL. Por eso los detalles están restringidos con
`show-details=when-authorized` + `roles=ADMIN`. Abrir los endpoints de actuator
para facilitar la demo reabriría esa fuga.

Consecuencia práctica: **no puedes abrir la URL en el navegador y ya**. Saca un
token antes de empezar y usa curl/Postman.

```bash
# Token de admin desde Keycloak (ajusta realm, client y credenciales)
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/<realm>/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=<client>" \
  -d "username=<admin-user>" \
  -d "password=<password>" | jq -r .access_token)
```

> Saca el token **justo antes** de la demo: si caduca a mitad, te quedas colgado.

---

## Qué circuito usar: `tariffService`

Es el más cómodo de los cuatro para enseñar en vivo:

- **`wait-duration-in-open-state: 20s`** — el doble que `vehicleService` y
  `spotService` (10s). Te da margen para narrar el estado OPEN antes de que
  transicione solo a HALF_OPEN.
- **Solo interviene en check-out**, así que controlas exactamente cuándo se
  dispara: mientras no hagas un check-out, no se acumulan fallos.
- Abre con solo **5 llamadas fallidas** (`minimum-number-of-calls: 5`,
  `failure-rate-threshold: 50`), frente a las 10 de vehicle/spot.

`ticketService` tiene `wait-duration-in-open-state: 30s`, aún más margen, pero
es un servicio secundario y su fallo no bloquea el check-out, así que el efecto
visible para el público es menor.

---

## Guion paso a paso

### 1. Estado inicial — CLOSED

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/actuator/circuitbreakers | jq
```

Los cuatro circuitos en `"state": "CLOSED"`.

### 2. Provocar el fallo — tumbar tariff-service

```bash
docker compose stop tariff-service
```

### 3. Abrir el circuito — CLOSED → OPEN

Haz **5 check-out** seguidos (los 5 fallarán). A partir del quinto el circuito
abre.

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/actuator/circuitbreakers | jq '.circuitBreakers.tariffService'
```

`"state": "OPEN"`.

**Lo interesante de enseñar aquí:** el siguiente check-out ya **no espera al
timeout**. Falla de inmediato con 503, porque el circuito corta la llamada sin
intentarla. Compara el tiempo de respuesta con el de los 5 primeros — esa
diferencia es el valor del patrón.

### 4. Esperar 20 s — OPEN → HALF_OPEN

No hace falta hacer nada:
`automatic-transition-from-open-to-half-open-enabled: true` hace la transición
sola aunque no llegue tráfico.

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/actuator/circuitbreakers | jq '.circuitBreakers.tariffService'
```

`"state": "HALF_OPEN"`.

### 5. Recuperar — HALF_OPEN → CLOSED

```bash
docker compose start tariff-service
```

Haz un par de check-out correctos. El circuito vuelve a `CLOSED`.

### 6. Rematar con el histórico completo

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/actuator/circuitbreakerevents | jq \
  '.circuitBreakerEvents[] | select(.circuitBreakerName=="tariffService")'
```

Se ve la secuencia entera de transiciones con sus marcas de tiempo. Es la
diapositiva final de la demo.

---

## Detalles que conviene tener claros por si preguntan

**Un circuito abierto NO tumba el health check.**
`allow-health-indicator-to-fail: false` está puesto a propósito: un circuito
abierto describe un problema del *servicio destino*, no de stay-service. Si
pusiera `/actuator/health` en DOWN, Kubernetes reiniciaría stay-service, que es
exactamente lo contrario de lo que busca un circuit breaker.

**Los errores de negocio no abren el circuito.**
Cada instancia declara `record-exceptions` con únicamente su
`*ServiceException` de infraestructura. Un parking lleno (`NoAvailableSpotException`)
o una tarifa no configurada (`NoActiveTariffException`) cuentan como llamada
**correcta**: son respuestas válidas de negocio, no fallos del servicio destino.

**Los circuitos están aislados entre sí.**
Hay una instancia por servicio destino. Que tariff-service esté caído no impide
que stay-service siga hablando con spot-service.

**Las sondas de Kubernetes no se ven afectadas.**
`/actuator/health/liveness` y `/actuator/health/readiness` siguen siendo
públicas y no necesitan detalles.

---

## Si falla el día de la demo

**404 en `/actuator/circuitbreakers`** → alguien tocó
`management.endpoints.web.exposure.include` y se dejó fuera `circuitbreakers`.
Lo cubre `CircuitBreakerActuatorExposureTest`, así que el build debería haberlo
cazado antes.

**401** → token caducado o ausente. Vuelve a sacarlo.

**403 en `/actuator/health` con detalles** → tu usuario no tiene rol ADMIN
(`management.endpoint.health.roles=ADMIN`). Usa `/actuator/circuitbreakers`,
que solo pide estar autenticado.

**Nada responde a través de Kong** → confirma que la ruta `/actuator` está
enrutada en el gateway y que Kong no se está comiendo la cabecera
`Authorization`. Es el fallo más típico y no se ve hasta que lo pruebas: **haz
esta comprobación el día antes, no en la demo**.

---

## Cobertura automática (y qué queda manual)

`CircuitBreakerActuatorExposureTest` blinda la parte que se rompe por descuido:

- que `management.endpoints.web.exposure.include` siga publicando
  `circuitbreakers` y `circuitbreakerevents`;
- que `management.health.circuitbreakers.enabled` siga activo;
- que los tres estados del criterio de aceptación (CLOSED, OPEN, HALF_OPEN)
  sean alcanzables, que es exactamente el `getState()` que serializa el endpoint.

Las dos primeras comprobaciones leen **el fichero de configuración de
despliegue** (`src/main/resources/application.properties`), no el `Environment`
de Spring. Es a propósito: `src/test/resources/application.properties` tapa al
de `src/main` en el classpath de test, así que preguntarle al `Environment`
daría un falso verde — vería la config de test, que no despliega nada.

**Lo que NO cubre el build: la capa HTTP.** No hay test que haga un GET real a
`/actuator/circuitbreakers`. Se intentó con MockMvc y no es viable: en
`@SpringBootTest` con entorno MOCK el handler mapping de actuator no queda
registrado en el DispatcherServlet del test, la petición cae en el handler de
recursos estáticos y acaba en 500. Hacerlo de verdad exigiría levantar servidor
real (`RANDOM_PORT`) y un JWT auténtico.

Consecuencia práctica: **haz el ensayo completo el día antes de la demo**,
siguiendo el guion de arriba de principio a fin. El build te avisa si alguien
desactiva la exposición, pero no si Kong deja de enrutar `/actuator` o si el
token no tiene el rol correcto.
