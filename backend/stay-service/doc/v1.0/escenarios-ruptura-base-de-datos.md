# Escenarios de ruptura a nivel de base de datos — análisis previo

Revisión de stay-service (y comparativa con los otros cuatro) buscando
respuestas inesperadas de la base de datos que hoy no están controladas.
**Documento de análisis: todavía no se ha implementado nada.**

## Punto de partida: la premisa de la historia matiza en stay-service

Conviene decirlo antes de nada, porque cambia dónde hay que invertir el
esfuerzo. En stay-service **ya existe una red de seguridad**:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request)
```

Devuelve un 500 estructurado con mensaje genérico y registra el detalle solo en
el log del servidor. Por la ruta normal de Spring MVC, **hoy no llega un
stacktrace de base de datos al frontend**.

Lo que sí está roto es otra cosa, y es más sutil: todo lo relacionado con la
base de datos se clasifica igual (500), hay rutas que se saltan el adapter, y la
cobertura es casi inexistente. En los otros servicios la premisa sí se cumple al
pie de la letra.

---

## A. Todo fallo de base de datos es un 500 indistinguible

No hay **ni un solo handler** de `DataAccessException` en ninguno de los cinco
servicios. Todo cae en la red genérica.

| Situación | Excepción de Spring | Hoy | Debería |
|---|---|---|---|
| Postgres caído / inalcanzable | `CannotCreateTransactionException`, `DataAccessResourceFailureException` | 500 | **503** |
| Timeout de consulta | `QueryTimeoutException` | 500 | 503 / 504 |
| Deadlock | `DeadlockLoserDataAccessException` | 500 | 409 (reintentable) |
| Lock no adquirido | `CannotAcquireLockException`, `PessimisticLockingFailureException` | 500 | 409 / 503 |
| Pool de conexiones agotado | `DataAccessResourceFailureException` | 500 | 503 |
| Query mal formada, mal uso de la API | `InvalidDataAccessApiUsageException`, `JpaSystemException` | 500 | 500 (correcto: es bug nuestro) |

**La incoherencia que esto produce.** Si spot-service se cae, el frontend recibe
un 503 con «servicio de plazas no disponible». Si se cae **la base de datos**,
recibe «Ha ocurrido un error inesperado». El operario del tótem no puede
distinguir *espera y reintenta* de *hay un bug*, cuando el primero es
exactamente el mismo tipo de fallo de infraestructura que ya sabemos comunicar
bien para los servicios externos.

**Además, solo `save` tiene traducción.** El resto de operaciones del adaptador
—`findById`, `findAll`, `existsByVehicleIdAndStatus`, `findByVehicleIdAndStatus`
y `findPage`— no tienen ningún `try`/`catch`.

---

## B. Errores de entrada que salen como 500

Sin handler específico, estos caen también en la red genérica:

| Petición | Excepción | Hoy | Debería |
|---|---|---|---|
| `GET /v1/stays/no-es-un-uuid` | `MethodArgumentTypeMismatchException` | 500 | 400 |
| Cuerpo JSON malformado | `HttpMessageNotReadableException` | 500 | 400 |
| `GET /v1/stays?status=INVENTADO` | `MethodArgumentTypeMismatchException` | 500 | 400 |
| `GET /v1/stays?page=-1` o `size=0` | `IllegalArgumentException` (de `PageRequest.of`) | 500 | 400 |
| Falta un `@RequestParam` obligatorio | `MissingServletRequestParameterException` | 500 | 400 |
| Método HTTP incorrecto | `HttpRequestMethodNotSupportedException` | 500 | 405 |

**Sin tope en `size`.** `GET /v1/stays?size=1000000` dispara una consulta enorme
y, encima, el N+1 contra vehicle-service que `ListStaysUseCase` documenta como
aceptable *«acotado por size, 20 por defecto»*. Esa acotación no existe: el
cliente elige el número.

---

## C. Dos bugs colaterales encontrados

1. **`checkOut` no lleva `@Valid`** en su `@RequestBody`, a diferencia de
   `checkIn`.
2. **`StayCheckOutRequest` no tiene ninguna anotación de validación** (campos
   públicos, sin `@NotBlank`).

Que hoy una matrícula vacía acabe en un 400 correcto es casualidad: lo salva
`normalizePlate` dentro del caso de uso, no la capa de entrada.

---

## D. Rutas que sí se saltan el `@RestControllerAdvice`

Aquí es donde la historia acierta de lleno.

- **Excepciones lanzadas en los filtros** (`CorrelationIdFilter`,
  `RequestIdentityFilter`, `RequestLoggingFilter`). Corren antes del
  `DispatcherServlet`, así que `@RestControllerAdvice` no aplica. Acaban en el
  `/error` del contenedor y devuelven el cuerpo por defecto de Spring, **no**
  nuestro `ErrorResponse`. Rompe el contrato del `openapi.yml`.
- **`Error` no es `Exception`.** `@ExceptionHandler(Exception.class)` no captura
  `Throwable` ni `Error`. Un `OutOfMemoryError` o un `StackOverflowError` se van
  por `/error`.
- **No hay ninguna property `server.error.*` configurada.** Hoy los valores por
  defecto de Spring Boot son seguros (`include-stacktrace=never`), pero es una
  garantía implícita que nadie ha declarado ni fijado con un test.

---

## E. Una fuga real, por una puerta que nadie está mirando

```properties
management.endpoint.health.show-details=always
```
```java
.requestMatchers("/actuator/health/**").permitAll()
```

Cualquiera **sin autenticar** puede ver el detalle del componente `db`. Cuando
la base de datos falla, `DataSourceHealthIndicator` incluye el mensaje de la
excepción en el campo `error` de la respuesta.

Eso es, literalmente, detalle interno de base de datos saliendo al exterior:
justo lo que la historia quiere evitar, pero por una vía distinta de la que se
está mirando. Se corrige con `show-details=when-authorized`.

---

## F. Cobertura

`CustomizedExceptionAdapterTest` tiene **8 tests para 15 handlers**. Entre lo no
cubierto está `handleUnexpected`, que es precisamente la red de seguridad de la
que depende todo lo demás.

Y falta el test que de verdad importa: uno que afirme, para una batería de
excepciones representativas, que **ninguna respuesta de error contiene detalle
interno** (nombres de tabla, SQL, clases de excepción, rutas de fichero).

---

## G. Estado de los otros cuatro servicios

| Servicio | Handlers | Red `Exception` | Cuerpo de error |
|---|---|---|---|
| stay | 15 | Sí | `ErrorResponse` |
| spot | 7 | Por confirmar | `ErrorResponse` |
| tariff | 2 | **No** | `ErrorResponse` |
| vehicle | 2 | **No** | **`String` en crudo** |
| ticket | **0** | **No** | — |

En **vehicle-service** la premisa de la historia se cumple sin matices: sin red
genérica, un `DataIntegrityViolationException` sale como la página whitelabel de
Spring. Es el mismo hallazgo del ticket de condiciones de carrera visto desde
otro ángulo, y con la misma consecuencia: ese 500 llega a stay-service como
`VehicleServiceException` y acaba en un 503 engañoso.

---

## H. Trampa que dejó abierta el ticket de condiciones de carrera

`StayPersistenceAdapter.save` es `@Transactional` y captura la violación de
integridad para relanzar `DuplicateActiveStayException`.

Funciona **hoy** porque el caso de uso no es transaccional. Si alguien añade
`@Transactional` a `CheckInUseCase` y en algún punto se traga esa excepción,
Spring lanzará `UnexpectedRollbackException` al hacer commit y el 409 se
convertirá en 500 sin que nada avise.

No es un bug actual. Es una trampa que merece un test que la fije.

---

## Propuesta de trabajo, por orden de valor

1. **Handlers de `DataAccessException`** con la jerarquía correcta: 503 para
   fallos de infraestructura, 409 para conflictos transitorios, 500 para bugs
   propios. Es el núcleo de la historia.
2. **Handlers de los errores de Spring MVC** (400/405 en vez de 500), tope
   máximo a `size`, y `@Valid` + anotaciones en el check-out.
3. **`ErrorResponse` también fuera del advice**: `ErrorController` propio, y
   filtros que no puedan reventar sin traducir.
4. **`show-details=when-authorized`** en el actuator.
5. **Cobertura**, incluido un test parametrizado que afirme la propiedad
   «ninguna respuesta de error lleva detalle interno».
6. Lo mismo en los otros cuatro servicios, o solo el informe.
