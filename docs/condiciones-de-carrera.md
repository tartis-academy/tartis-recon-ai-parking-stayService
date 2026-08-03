# Condiciones de carrera en Parking S4

Revisión de los cinco microservicios buscando operaciones concurrentes que leen
y escriben el mismo dato sin coordinación. En stay-service las dos que se
encontraron ya están corregidas (ver la última sección). Las de los otros cuatro
servicios se documentan aquí para que sus equipos las conviertan en tickets.

## Qué se ha buscado

Tres patrones concretos:

1. **Comprobar y luego escribir (TOCTOU).** Un `existsBy...` o un `findBy...`
   seguido de un `save`, con la decisión tomada a partir de la lectura. Entre
   ambas operaciones cabe otra petición.
2. **Leer, modificar y escribir sin versión.** Cargar una entidad, cambiarle un
   campo y guardarla. Dos peticiones simultáneas leen el mismo estado de partida
   y la segunda escritura pisa a la primera sin dejar rastro (*lost update*).
3. **Efectos externos antes de confirmar.** Publicar un evento o llamar a otro
   servicio antes de que la escritura esté asegurada. Si luego la escritura se
   rechaza, el efecto ya salió y no se puede recoger.

Un apunte que condiciona todo lo demás: **ningún bloqueo en memoria de la JVM
sirve aquí**. Los servicios se despliegan con varias réplicas, así que un
`synchronized` o un `ConcurrentHashMap` solo coordina las peticiones que caen en
la misma instancia. El único punto que ven todas las réplicas a la vez es
PostgreSQL.

---

## vehicle-service

Es donde está el ejemplo literal de la historia: *dos administradores registran
el mismo coche en el mismo segundo*.

### V1. Alta duplicada de matrícula sale como 500 (prioridad alta)

`CreateVehicleUseCase.execute` comprueba `existsByPlate` y luego llama a `save`.
Dos altas simultáneas de la misma matrícula pasan las dos la comprobación.

La buena noticia es que la columna **sí** es única (`@Column(unique = true)` en
`VehicleEntity`, y `plate VARCHAR(255) NOT NULL UNIQUE` en `db/schema.sql`), así
que Postgres impide el duplicado. El problema es lo que pasa después:
`CustomizedExceptionAdapter` solo declara handlers para `VehicleNotFoundException`
e `InvalidVehicleException`, así que la `DataIntegrityViolationException` sale
como **500 sin traducir**.

Y el 500 no se queda ahí. `StayVehicleClientAdapter.getOrCreateVehicle`, en
stay-service, traduce cualquier 5xx de vehicle-service a `VehicleServiceException`,
que acaba siendo un **503 "vehicle-service no disponible"**. O sea: un check-in
legítimo de una matrícula nueva falla con la barrera cerrada y un mensaje que
apunta al sitio equivocado, igual que pasó con el problema de credenciales del
30/07.

Propuesta: handler para `DataIntegrityViolationException` que devuelva 409
Conflict con un mensaje de matrícula duplicada.

### V2. No hay transacciones en ningún sitio (prioridad alta)

No aparece un solo `@Transactional` en todo el servicio. `existsByPlate` y
`save` se ejecutan en dos transacciones autocommit distintas, lo que hace la
ventana de la carrera todo lo ancha que puede ser. Lo mismo en
`UpdateVehicleUseCase`, que encadena tres accesos a base de datos.

### V3. Dos administradores editando el mismo vehículo (prioridad media)

`UpdateVehicleUseCase` hace `findById`, construye el objeto actualizado y
guarda. `VehicleEntity` no tiene `@Version`, así que dos ediciones simultáneas
del mismo vehículo terminan con los cambios del primero desaparecidos y sin
ningún aviso. Igual en `DeleteVehicleUseCase.deactivate`.

Merece la pena mirar además la interacción con RN-11: si un administrador da de
baja un vehículo justo mientras ese vehículo está haciendo check-in, el
`getOrCreateVehicle` puede haber leído `active = true` una fracción de segundo
antes de la baja. El coche entra. No es grave (la baja lógica es una decisión
administrativa, no una emergencia), pero conviene decidirlo explícitamente en
vez de que quede al azar.

---

## spot-service

**Es el mejor de los cinco y conviene decirlo**: `findAndOccupyAvailableSpot`
usa `SELECT ... FOR UPDATE` con `SKIP LOCKED` y `@Transactional` en el caso de
uso. Ocupar una plaza es atómico de verdad, y `SKIP LOCKED` además evita que las
peticiones concurrentes se peleen por la misma fila: cada una se lleva una plaza
distinta sin esperar. Es el patrón a citar como referencia interna.

### S1. Liberar y bloquear una plaza a la vez (prioridad alta)

`ReleaseSpotUseCase` y `UpdateSpotStatusUseCase` hacen `findById`, mutan el
`Spot` en memoria y guardan. Los dos son `@Transactional`, pero **ninguno bloquea
la fila ni usa versión**, y `SpotEntity` no tiene `@Version`.

Las guardas del dominio (`release()` exige OCCUPIED, `blockForMaintenance()`
exige AVAILABLE) no protegen de nada aquí, porque cada transacción las evalúa
contra su propia lectura y las dos ven el estado antiguo.

El escenario que duele: un coche sale (`release`, la plaza pasa a AVAILABLE) a la
vez que un administrador la manda a mantenimiento (`blockForMaintenance`, pasa a
UNAVAILABLE). Según cuál escriba último, queda:

- **UNAVAILABLE con la plaza en realidad libre**: se pierde una plaza del
  inventario hasta que alguien lo note a mano.
- **AVAILABLE con un coche dentro**: peor, porque `findAndOccupyAvailableSpot` se
  la asignará a un segundo vehículo y habrá dos coches para la misma plaza.

Propuesta: `@Version` en `SpotEntity`, o reusar el `PESSIMISTIC_WRITE` que ya
tiene el repositorio para estas dos rutas.

### S2. Doble liberación de la misma plaza (prioridad media)

Relacionado con lo anterior y con la carrera de check-out de stay-service: dos
`release` simultáneos de la misma plaza pasan los dos la guarda `release()`. El
resultado final es idempotente (AVAILABLE en ambos casos), así que por sí solo no
rompe nada; el riesgo aparece cuando la segunda liberación llega tarde, después
de que otro coche haya ocupado ya esa plaza.

### S3. El adaptador descarta la entidad gestionada (prioridad baja)

`SpotPersistenceAdapter.findAndOccupyAvailableSpot` hace
`repository.save(mapper.toEntity(spot))`, es decir, construye una **entidad nueva
y desasociada** en lugar de mutar la que acaba de bloquear. Hoy funciona, pero si
se añade `@Version` (S1) este código dejará la versión a null y romperá el
bloqueo optimista sin avisar. Conviene arreglarlo en la misma tarea.

---

## tariff-service

El más expuesto de los cuatro, y el único donde la carrera afecta directamente a
lo que se cobra.

### T1. No se garantiza una sola tarifa activa por tipo de vehículo (prioridad alta)

No hay nada, ni en el dominio ni en la base de datos, que impida tener dos
tarifas activas para el mismo tipo de vehículo. `findActiveByType` devuelve una
`List`, y `ActivateTariffUseCase` se limita a poner `active = true` en la que le
pidan.

Dos administradores activando dos tarifas de COCHE en el mismo segundo dejan las
dos activas. A partir de ahí, **el precio que se le cobra al cliente depende del
orden en que Postgres devuelva las filas**, que no está garantizado. No hace
falta ni concurrencia para llegar a este estado: basta con activar una sin
desactivar la otra.

Propuesta: índice único parcial `UNIQUE (type) WHERE active = true`, y que
activar una tarifa desactive la anterior del mismo tipo en la misma transacción.
Es el mismo patrón que se ha usado en stay-service para la estancia activa.

### T2. Alta duplicada de nombre sale como 500 (prioridad media)

`CreateTariffUseCase` **ni siquiera comprueba** `existsByName` antes de guardar,
aunque el puerto expone el método y la columna es `UNIQUE`. El choque salta
directamente como `DataIntegrityViolationException` sin traducir.

### T3. Sin transacciones ni versión en las modificaciones (prioridad media)

`Activate`, `Deactivate` y `UpdateTariffUseCase` hacen leer-modificar-escribir
sin `@Transactional` y sin `@Version` en `TariffEntity`. Dos ediciones
simultáneas de la misma tarifa: gana la última y la otra desaparece.

---

## ticket-service

Está prácticamente sin implementar: `IssueEntryTicketUseCase`,
`UseEntryTicketUseCase`, `MarkEntryTicketLostUseCase`, `CreateTicketUseCase`,
`TicketEntity` y `EntryTicketPersistenceAdapter` son clases vacías. No hay
código concurrente que arreglar todavía.

Sí conviene dejar anotado lo que ya está bien encaminado y lo que habrá que
vigilar cuando se implemente:

- `EntryTicketEntity` ya declara `unique` en `stayId` (IN-20, relación 1:1 con la
  estancia) y en `code`. Esas dos restricciones son exactamente la defensa
  correcta contra el doble ticket, y hay que **conservarlas**.
- `IssueEntryTicketUseCase` recibirá la petición desde el check-in de
  stay-service. El `unique` sobre `stayId` lo hace naturalmente idempotente: si
  llega dos veces la misma estancia, la segunda debe devolver el ticket ya
  existente en vez de fallar.
- `EntryTicketCodeGenerator` usa `SecureRandom` sobre un alfabeto de 30 símbolos
  y longitud 10. La probabilidad de colisión es despreciable, pero el `unique`
  sobre `code` cubre el caso; hay que capturarlo y reintentar, no dejarlo salir
  como 500.
- El consumidor de `StayClosedEvent` tendrá que ser idempotente por su cuenta:
  RabbitMQ garantiza *at-least-once*, así que el mismo evento puede llegar dos
  veces aunque stay-service lo publique una sola.

---

## Qué se ha corregido en stay-service

Implementado en la rama `feature/condiciones-de-carrera`.

### Carrera de entrada: doble check-in del mismo vehículo

`CheckInUseCase` comprobaba `existsByVehicleIdAndStatus` y guardaba varias
llamadas HTTP después. Dos check-ins simultáneos de la misma matrícula pasaban
los dos la comprobación, ocupaban **dos plazas** y creaban **dos estancias
activas** con dos tickets de entrada válidos.

Se cierra con un **índice único parcial** en `V2__race_conditions.sql`:

```sql
CREATE UNIQUE INDEX ux_stays_one_active_per_vehicle
    ON stays (vehicle_id)
    WHERE status = 'IN_PROGRESS';
```

Es parcial a propósito: sin la cláusula `WHERE`, un vehículo no podría volver a
entrar nunca después de su primera estancia. `StayPersistenceAdapter` traduce la
violación a `DuplicateActiveStayException`, la misma excepción que ya lanzaba la
comprobación previa, así que el cliente recibe el mismo 409 se detecte donde se
detecte. La compensación existente libera la plaza que se había ocupado.

La comprobación en Java **no se ha quitado**: resuelve el caso normal sin ocupar
plaza ni emitir ticket para luego tener que deshacerlo. El índice cubre solo el
caso raro de las dos peticiones simultáneas.

### Carrera de salida: doble check-out de la misma estancia

`CheckOutUseCase` leía la estancia, llamaba a tariff-service y guardaba. Dos
salidas simultáneas leían las dos IN_PROGRESS y provocaban tres cosas: la segunda
escritura pisaba a la primera, se publicaban **dos `StayClosedEvent`** (dos
tickets de salida) y spot-service liberaba la plaza **dos veces** — la segunda
liberación, potencialmente, sobre un coche que ya había entrado después.

Se cierra con bloqueo optimista: `@Version` en `StayEntity` y un token de versión
que viaja dentro del propio agregado `Stay` desde la lectura hasta la escritura.
El detalle importante es *por qué* el token tiene que viajar: si la versión se
releyera en el momento de guardar, se leería la ya incrementada por la otra
petición y el UPDATE la pisaría igualmente.

El orden guardar-antes-de-publicar, que ya estaba, es lo que hace que esto
funcione: la petición que pierde la carrera revienta en el `save` y no llega al
`publish`. Si el evento saliera primero, el bloqueo optimista no serviría de
nada.

### Cómo se ha probado

`StayConcurrencyTest` lanza ocho hilos simultáneos contra una PostgreSQL real
(Testcontainers) y verifica que solo uno gana en cada caso. Hace falta Postgres
de verdad porque el índice parcial **no existe en H2**: sobre H2 el test pasaría
en verde sin haber probado nada. Un test con Mockito que simule la excepción
comprueba que sabemos escribir un `catch`, no que la base de datos vaya a
lanzarla.
