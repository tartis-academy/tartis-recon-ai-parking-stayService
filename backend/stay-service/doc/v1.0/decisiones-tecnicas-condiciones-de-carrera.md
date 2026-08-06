# Condiciones de carrera — modificaciones y decisiones técnicas

**Rama:** `feature/condiciones-de-carrera` (8 commits sobre `release`)
**Alcance:** stay-service. Los otros cuatro microservicios se revisaron pero no
se tocaron; sus hallazgos están en [`condiciones-de-carrera.md`](condiciones-de-carrera.md).
**Diff:** 20 ficheros, +1267 / −25.

---

## 1. El problema

Una condición de carrera ocurre cuando dos operaciones concurrentes leen y
escriben el mismo dato sin coordinación, y el resultado depende del orden exacto
en que se ejecutan. En stay-service había dos, ambas con la misma forma: una
comprobación, luego varias llamadas HTTP, y solo entonces la escritura.

**Carrera de entrada — doble check-in del mismo vehículo.**
`CheckInUseCase` comprobaba `existsByVehicleIdAndStatus` en el paso 3 y guardaba
en el paso 7, con tres llamadas HTTP en medio (vehicle, spot, tariff, ticket).
Dos check-ins simultáneos de la misma matrícula pasaban los dos la comprobación,
porque en el momento de mirar ninguno había guardado todavía. Resultado: el mismo
coche dentro dos veces, **ocupando dos plazas físicas** y con dos tickets de
entrada válidos. No había ningún constraint en base de datos que lo impidiera.

**Carrera de salida — doble check-out de la misma estancia.**
`CheckOutUseCase` leía la estancia, llamaba a tariff-service para calcular el
importe y guardaba. Dos salidas simultáneas leían las dos `IN_PROGRESS` y
provocaban tres cosas distintas:

1. La segunda escritura pisaba a la primera (*lost update*). Si el reloj avanzó
   entre ambas, el importe cobrado no es el que se calculó.
2. Se publicaban **dos `StayClosedEvent`**, así que ticket-service emitía dos
   tickets de salida para la misma estancia.
3. spot-service liberaba la plaza **dos veces**. Esta es la peor: si entre medias
   entró otro coche a esa plaza, queda marcada como libre con un vehículo dentro
   y se le asignará a un tercero.

---

## 2. Decisiones técnicas

### D1 — La base de datos como árbitro, no la aplicación

**Decisión.** Las garantías fuertes viven en PostgreSQL (índice único parcial y
columna de versión), no en código Java.

**Por qué.** stay-service se despliega con varias réplicas y cada una tiene su
propia JVM. Cualquier coordinación en memoria — `synchronized`, un
`ConcurrentHashMap` de matrículas, un lock por vehículo — solo cubre las
peticiones que caen en la misma instancia. El único punto que ven todas las
réplicas a la vez es la base de datos.

**Alternativas descartadas.**

- *Locks en memoria por matrícula.* Descartada de entrada por lo anterior. Daría
  una falsa sensación de seguridad: funcionaría en local con una instancia y
  fallaría en producción.
- *Lock distribuido (Redis, etcétera).* Resuelve el problema, pero añade una
  pieza de infraestructura nueva y un modo de fallo nuevo (¿qué pasa si Redis no
  responde?) para un caso que la base de datos ya sabe resolver sola.

### D2 — Índice único **parcial** para la estancia activa

```sql
CREATE UNIQUE INDEX ux_stays_one_active_per_vehicle
    ON stays (vehicle_id)
    WHERE status = 'IN_PROGRESS';
```

**Por qué parcial.** La unicidad solo debe aplicar a las estancias vivas. Un
vehículo puede y debe poder acumular cientos de estancias `FINISHED` históricas.
Un índice único sobre `vehicle_id` a secas impediría que ese coche volviera a
entrar nunca después de su primera visita.

Esto es también la razón de que el índice **no** se pueda declarar en JPA:
`@Table(uniqueConstraints = ...)` no admite condición. La entidad declara solo un
índice de apoyo no único; la garantía real vive en la migración de Flyway.

**Sobre el despliegue.** Si la tabla ya tuviera duplicados, el `CREATE` falla y
la migración se detiene. Es intencionado: preferimos parar el despliegue a
saltarnos la garantía en silencio. La consulta para localizarlos está en el
propio fichero SQL.

### D3 — La comprobación previa en Java **se queda**

**Decisión.** `existsByVehicleIdAndStatus` sigue en `CheckInUseCase`, aunque el
índice cubra el caso.

**Por qué.** Resuelve el caso normal — el coche lleva dentro un rato — sin ocupar
una plaza ni emitir un ticket para luego tener que deshacerlo. El índice cubre
solo el caso raro de las dos peticiones simultáneas. Quitarla convertiría cada
intento de reentrada legítimo en una ocupación de plaza más su compensación.

Ambos caminos lanzan la **misma** `DuplicateActiveStayException`, así que el
cliente recibe el mismo 409 se detecte donde se detecte.

### D4 — Bloqueo optimista, y el token viaja por el dominio

**Decisión.** `@Version` en `StayEntity`, y un campo `version` en el agregado
`Stay` que se arrastra desde la lectura hasta la escritura (`finish()` y
`cancel()` lo conservan).

**Por qué el token tiene que viajar.** Esta es la decisión menos obvia del
cambio. Meter `version` en el dominio parece contaminarlo con un detalle de
persistencia, y sería tentador dejarlo solo en la capa de infraestructura, que el
adaptador releyera la fila al guardar y comparase. **No funcionaría.** En un
check-out hay una llamada HTTP a tariff-service entre la lectura y la escritura;
si la versión se releyera en el momento de guardar, se leería la *ya
incrementada* por la otra petición y el UPDATE la pisaría igualmente. Solo
comparando contra la versión que se leyó al principio se detecta que alguien tocó
la fila mientras tanto.

**Por qué `Long` y no `long`.** Spring Data usa `version == null` como señal de
"entidad nueva" para decidir entre `persist()` (INSERT) y `merge()` (UPDATE).
Normalmente usaría `id == null`, pero aquí el id lo genera el dominio y nunca es
null. Con un `long` primitivo esa detección no funciona y **todos** los guardados
intentarían un INSERT.

**Alternativa descartada: bloqueo pesimista (`SELECT ... FOR UPDATE`).**
Funcionaría y es más fácil de explicar, pero obligaría a mantener la transacción
abierta durante las llamadas HTTP a spot, tariff y ticket. Eso convierte una
carrera poco probable en un cuello de botella permanente: con spot-service lento,
cada check-in retendría una fila bloqueada y una conexión del pool durante
segundos. spot-service sí usa `FOR UPDATE` con `SKIP LOCKED` para ocupar plaza, y
ahí es correcto porque la transacción no envuelve ninguna llamada de red.

### D5 — `saveAndFlush` en vez de `save`

**Decisión.** `StayPersistenceAdapter.save` usa `saveAndFlush`.

**Por qué.** Con `save()`, Hibernate puede retrasar el INSERT/UPDATE hasta el
commit, que ocurre *después* de que el método haya devuelto. La excepción saldría
fuera del `try`, no se traduciría, y llegaría al cliente como un 500 genérico.
`saveAndFlush` fuerza el SQL dentro del bloque donde podemos interpretarlo.

La transacción sigue siendo deliberadamente corta y cubre solo la escritura, por
el mismo motivo que en D4.

### D6 — La violación se identifica por nombre de índice

**Decisión.** El adaptador comprueba que el mensaje de la
`DataIntegrityViolationException` contenga `ux_stays_one_active_per_vehicle`
antes de traducirla; si no, la relanza tal cual.

**Por qué.** No toda violación de integridad es un conflicto de negocio. Un
`NOT NULL` incumplido es un bug nuestro y debe seguir subiendo como 500;
disfrazarlo de 409 lo dejaría escondido para siempre. Se busca en el texto y no
por tipo de excepción porque cada driver y cada versión de Hibernate colocan el
nombre de la restricción en un punto distinto de la cadena de causas.

### D7 — 409 Conflict, no 500

**Decisión.** Ambas carreras se traducen a `409`.

**Por qué.** No se ha roto nada. La otra petición **sí** se completó
correctamente; lo único que ha pasado es que este cambio se descarta para no
pisarla. La diferencia práctica está en lo que le dice al operario del tótem: un
409 significa "esta salida ya se ha registrado, consulta el estado", un 500
significaría "algo se ha roto".

El contrato OpenAPI documenta además un matiz del check-out: en el caso
concurrente el cliente debe **releer** el estado, no reintentar, porque un
reintento devolvería 404 (la estancia ya no está `IN_PROGRESS`).

### D8 — El orden guardar-antes-de-publicar es lo que hace funcionar todo

**Decisión.** No se cambió el orden de `CheckOutUseCase` (ya guardaba antes de
publicar), pero se documentó como invariante.

**Por qué importa.** La petición que pierde la carrera revienta en el `save` y no
llega nunca al `publish`. Si el evento se publicara primero, el bloqueo optimista
**no serviría de nada**: el segundo ticket de salida y la segunda liberación de
plaza ya se habrían ido por el broker antes de que la base de datos tuviera
ocasión de decir que no. Hay un test dedicado a esta propiedad para que nadie
invierta el orden por descuido.

### D9 — Un ticket huérfano asumido a propósito

**Situación.** En el check-in, el ticket de entrada se emite (paso 6) *antes* de
guardar la estancia (paso 7). Si la BD rechaza el duplicado, el ticket ya salió y
no se puede anular: ticket-service no expone esa operación.

**Decisión.** Se asume, se registra en el log con nivel `warn` y se documenta.

**Por qué.** La alternativa — guardar antes de emitir el ticket — cambia un fallo
rarísimo por uno peor y más frecuente: si ticket-service está caído quedaría una
estancia en base de datos sin ticket, y esa estancia bloquearía **todos** los
reintentos de ese vehículo por IN-02. Un ticket suelto no bloquea nada; solo
ensucia el listado.

### D10 — Tests de concurrencia con PostgreSQL real, no con mocks

**Decisión.** `StayConcurrencyTest` lanza 8 hilos simultáneos contra una
PostgreSQL levantada con Testcontainers.

**Por qué no H2.** El índice único parcial **no existe en H2**. Sobre H2 el doble
check-in sencillamente no se detectaría y el test pasaría en verde sin haber
probado nada. Además H2 no reproduce el aislamiento ni el bloqueo de filas de
Postgres, que es justo el comportamiento bajo prueba.

**Por qué no mocks.** Un test con `when(repo.saveAndFlush(...)).thenThrow(...)`
comprueba que sabemos escribir un `catch`, no que la base de datos vaya a lanzar
esa excepción. Lo que puede estar mal es precisamente eso: que el índice esté bien
definido, que la versión llegue de vuelta al UPDATE, y que Spring traduzca el
error del driver al tipo que capturamos. Eso solo se ve ejecutándolo.

**Detalle que evita un test intermitente.** Las lecturas de cada hilo van
**antes** de la barrera de sincronización, para que los ocho se lleven la misma
versión y la carrera quede en el UPDATE. Si se leyeran después, los hilos lentos
encontrarían la estancia ya cerrada y el test fallaría de vez en cuando.

### D11 — MapStruct se mantiene, con `unmappedTargetPolicy = ERROR`

**Decisión final.** `StayPersistenceMapper` sigue siendo una interfaz `@Mapper`,
con la política de propiedades no mapeadas en `ERROR`.

**Cómo se llegó aquí.** En una versión intermedia el mapper se convirtió a clase
escrita a mano, y luego se revirtió. Merece la pena dejarlo escrito porque el
riesgo de fondo sigue existiendo:

Si `toEntity` no copia el token de versión, Spring Data ve "entidad nueva", hace
`persist()` en vez de `merge()` y **todos los check-out revientan** con un INSERT
contra una PK que ya existe. Y por defecto una propiedad de destino sin mapear en
MapStruct es solo un *warning*: ese fallo pasaría la compilación en verde y
aparecería en ejecución.

La conversión a clase eliminaba la incertidumbre, pero a costa de salirse de la
convención del repositorio y de un diff mayor del que la historia pedía. La
solución adoptada consigue lo mismo sin renunciar a MapStruct:

- `unmappedTargetPolicy = ReportingPolicy.ERROR` convierte ese warning silencioso
  en un fallo de compilación.
- `StayEntity` conserva **un único constructor público**. Éste es el punto clave:
  MapStruct, cuando hay un solo constructor público, lo usa y asigna por setter
  las propiedades que no estén entre sus parámetros. Añadir un segundo
  constructor público obligaría a desambiguar con `@org.mapstruct.Default`, es
  decir, a meter una anotación de la librería de mapeo dentro de una entidad JPA.
- `version` se mapea sola por coincidencia de nombre.

`toDomain` sigue escrito a mano: la reconstrucción pasa por la factoría del
dominio, que valida los invariantes, en vez de rellenar campos con setters
saltándose esas comprobaciones.

### D12 — Coordenadas de Testcontainers 2.x, sin versión explícita

**Decisión.** Las dependencias se declaran con los nombres de módulo de la serie
2 (`testcontainers-junit-jupiter`, `testcontainers-postgresql`) y **sin**
`<version>`: la aporta el BOM de Spring Boot 4.1.

**El error que corrige, y por qué costó dos intentos localizarlo.** La primera
versión del POM usaba los nombres antiguos (`junit-jupiter`, `postgresql` a
secas) sin versión. La build fallaba, y ni la revisión de PR ni el análisis
inicial dieron con la causa real:

- La revisión concluyó que *«la versión 2.0.5 no existe para org.testcontainers,
  la línea actual es 1.19.x / 1.20.x»* y propuso fijar `1.19.8`. Eso compila,
  pero el diagnóstico es incorrecto y ancla el proyecto a una línea de 2024.
- El análisis inicial verificó que la propiedad `<testcontainers.version>=2.0.5`
  existe en `spring-boot-dependencies:4.1.0` — cierto — y **asumió** que ese
  valor servía para los artefactos declarados. No lo comprobó.

**Lo que zanja la cuestión** es consultar `maven-metadata.xml` de cada artefacto
en Maven Central. Una petición por artefacto, y no admite interpretación:

| Artefacto | Versiones publicadas |
|---|---|
| `org.testcontainers:junit-jupiter` | 1.10.0 → 1.21.4 |
| `org.testcontainers:postgresql` | 0.9.6 → 1.21.4 |
| `org.testcontainers:testcontainers-junit-jupiter` | 2.0.0 → 2.0.5 |
| `org.testcontainers:testcontainers-postgresql` | 2.0.0 → 2.0.5 |

En la serie 2, Testcontainers **renombró los módulos** con prefijo
`testcontainers-`. La versión `2.0.5` de Boot es correcta; lo que estaba mal
eran los nombres de los artefactos. Es decir, la intención original (no fijar
versión y dejar que Boot la gestione) era la buena desde el principio.

**Lo que no cambió en 2.x** son los paquetes Java: `PostgreSQLContainer` sigue en
`org.testcontainers.containers`, y `@Testcontainers` / `@Container` en
`org.testcontainers.junit.jupiter`. `StayConcurrencyTest` no necesitó tocarse,
confirmado por compilación.

**La lección, que aplica más allá de este caso.** Los dos diagnósticos fallidos
razonaron desde evidencia parcial: uno desde el conocimiento previo de qué
versiones «suele» tener una librería, otro desde el nombre de una propiedad. El
repositorio de artefactos es la fuente de verdad y responde en una petición.

---

## 3. Inventario de modificaciones

### Base de datos

| Fichero | Cambio |
|---|---|
| `db/migration/V2__race_conditions.sql` | **Nuevo.** Índice único parcial `ux_stays_one_active_per_vehicle`, columna `version BIGINT NOT NULL DEFAULT 0`, índice de apoyo `ix_stays_vehicle_id_status`. |

### Dominio

| Fichero | Cambio |
|---|---|
| `domain/stay/Stay.java` | Campo `version` (nullable). Sobrecarga `restore(...)` de 10 argumentos que lo acepta; la de 9 se mantiene y delega con `null`. `finish()` y `cancel()` lo conservan. Nuevo `getVersion()`. Queda fuera de `equals` y `toString`. |
| `domain/stay/exception/ConcurrentStayModificationException.java` | **Nueva.** Conflicto de concurrencia sobre una estancia. |

### Aplicación

| Fichero | Cambio |
|---|---|
| `usecase/CheckInUseCase.java` | Javadoc con la carrera de entrada. `catch` específico de `DuplicateActiveStayException` que registra el ticket huérfano y libera la plaza. `ticket` se declara fuera del `try` para poder leerlo desde el `catch`. Sin cambios de flujo. |
| `usecase/CheckOutUseCase.java` | Solo documentación: la carrera de salida y por qué el orden guardar-antes-de-publicar es un invariante. |

### Infraestructura

| Fichero | Cambio |
|---|---|
| `persistence/StayEntity.java` | `@Version Long version` con su getter/setter. `@Index` de apoyo en `@Table`. Javadoc sobre por qué el constructor público debe seguir siendo único. |
| `persistence/StayPersistenceMapper.java` | `unmappedTargetPolicy = ERROR`. `toDomain` arrastra `entity.getVersion()`. |
| `persistence/StayPersistenceAdapter.java` | `save` pasa a `saveAndFlush` y traduce `DataIntegrityViolationException` → `DuplicateActiveStayException` (solo si es nuestro índice) y `OptimisticLockingFailureException` → `ConcurrentStayModificationException`. |
| `CustomizedExceptionAdapter.java` | Handler para `ConcurrentStayModificationException` → 409, con `log.warn` para poder medir con qué frecuencia ocurre de verdad. |

### Contrato, build y CI

| Fichero | Cambio |
|---|---|
| `openapi.yml` | Los 409 de check-in y check-out documentan el caso concurrente. |
| `pom.xml` | `org.testcontainers:testcontainers-junit-jupiter` y `:testcontainers-postgresql` en scope `test`, sin versión (la aporta el BOM de Boot 4.1; ver D12) |
| `.github/workflows/ci.yml` | Comentario actualizado: ya no todos los tests son H2. No hace falta configurar nada, `ubuntu-latest` trae Docker. |

### Tests

| Fichero | Cambio |
|---|---|
| `StayConcurrencyTest.java` | **Nuevo.** 8 hilos contra PostgreSQL real: doble check-in, doble check-out, y que el índice parcial permite reentrar tras cerrar la estancia. |
| `CheckInUseCaseTest.java` | Al perder la carrera, se libera la plaza ocupada. |
| `CheckOutUseCaseTest.java` | Al perder la carrera, **no** se publica `StayClosedEvent`. |
| `StayPersistenceAdapterTest.java` | Las dos traducciones, y que un `NOT NULL` incumplido sigue saliendo sin disfrazar. |
| `StayPersistenceMapperTest.java` | Ida y vuelta del token de versión; estancia nueva sin versión; `finish()` la conserva. |
| `StayEntityTest.java` | Getter/setter de versión; fila nueva con versión null. |
| `CustomizedExceptionAdapterTest.java` | El conflicto de concurrencia es 409. |

---

## 4. Riesgos y trabajo pendiente

**El código se escribió sin poder compilarlo.** El entorno de desarrollo tenía
JDK 11 y el proyecto necesita 17+. Se verificaron a mano el balance sintáctico y
los imports de los 15 ficheros Java tocados.

**Estado de verificación tras la revisión de PR:**

- ✅ **Compila.** Confirmado. Esto valida además que los paquetes de
  Testcontainers no cambiaron en 2.x y que MapStruct resuelve `toEntity`
  completo pese a `unmappedTargetPolicy = ERROR` (si faltara alguna propiedad,
  la compilación habría fallado, que es justo para lo que se puso).
- ⬜ **Suite completa en verde** (`./mvnw verify`), incluido `StayConcurrencyTest`
  con Docker.
- ⬜ **Cobertura JaCoCo ≥ 90%**, que es un gate del `verify`.
- ⬜ **Comportamiento de `merge()` con versión desfasada**: es lo que debe
  disparar `OptimisticLockingFailureException`. Solo se ve ejecutando el test de
  concurrencia.

Esta limitación ya produjo un fallo real: la primera versión del POM usaba los
nombres de módulo de Testcontainers 1.x con la versión de la serie 2 (ver D12).
Lo detectó la revisión de PR, no la build.

**La migración V2 falla si ya hay duplicados** en demo o producción. Es
intencionado; la consulta para localizarlos está en el propio SQL.

**Historial de la rama.** Son 6 commits y el último revierte parte del primero
(D11). Nada está pusheado, así que se puede hacer squash al mergear.

**Fuera de alcance, evaluado y descartado para esta historia.**

- *Idempotencia por cabecera `Idempotency-Key`.* Resolvería el caso del tótem
  reintentando tras un timeout de forma más limpia que un 409, porque devolvería
  el resultado original en vez de un error. Toca el contrato REST y merece
  historia propia.
- *Ventana entre la baja lógica de un vehículo y su check-in.* Si un
  administrador da de baja un vehículo justo mientras ese vehículo entra, el
  `getOrCreateVehicle` puede haber leído `active = true` una fracción de segundo
  antes. El coche entra. No es grave — la baja lógica es una decisión
  administrativa, no una emergencia — pero conviene decidirlo explícitamente en
  vez de dejarlo al azar.

**Los otros cuatro microservicios** tienen carreras sin resolver, dos de ellas
más graves que las corregidas aquí: en vehicle-service el alta duplicada sale
como 500 sin traducir y llega a stay como un engañoso 503, y en tariff-service
nada impide dos tarifas activas del mismo tipo, con lo que el precio cobrado pasa
a depender del orden de la lista. Detalle y prioridades en
[`condiciones-de-carrera.md`](condiciones-de-carrera.md).
