-- Condiciones de carrera: la base de datos como arbitro final.
--
-- El problema que resuelve esta migracion no se puede resolver en Java. Los
-- casos de uso comprueban primero y escriben despues ("existe ya una estancia
-- en curso?" y luego INSERT), y entre esas dos operaciones hay una ventana en
-- la que otra peticion concurrente puede colarse: es la ventana clasica de
-- TOCTOU (time-of-check to time-of-use).
--
-- Ningun bloqueo en memoria del servicio sirve aqui, porque stay-service se
-- despliega con varias replicas y cada una tiene su propia JVM. El unico punto
-- que ven todas a la vez es Postgres, asi que es Postgres quien tiene que decir
-- que no.
--
-- Las comprobaciones en Java NO se quitan: son las que dan un mensaje de
-- negocio claro en el 99,99% de los casos, y las que evitan ocupar una plaza
-- para nada. Lo de aqui es la ultima linea de defensa para el 0,01% restante.


-- ---------------------------------------------------------------------------
-- 1. Un vehiculo, como maximo una estancia en curso (IN-02, CB-05, RN-05).
-- ---------------------------------------------------------------------------
-- Carrera que cierra: dos check-in de la misma matricula en el mismo instante
-- (dos operarios en dos totems, o el mismo totem reintentando tras un timeout).
-- Los dos pasan el existsByVehicleIdAndStatus porque ninguno ha insertado
-- todavia, los dos ocupan una plaza distinta y los dos insertan. Resultado: el
-- mismo coche dentro dos veces, ocupando dos plazas fisicas y con dos tickets
-- de entrada validos.
--
-- El indice es PARCIAL (clausula WHERE) a proposito: la unicidad solo debe
-- aplicar a las estancias vivas. Un vehiculo puede y debe poder acumular
-- cientos de estancias FINISHED historicas; un indice unico sobre vehicle_id a
-- secas impediria que ese coche volviera a entrar nunca mas.
--
-- Nota de despliegue: si la tabla ya tuviera duplicados de antes, este CREATE
-- falla y la migracion se detiene. Es intencionado, preferimos parar el
-- despliegue a saltarnos la garantia en silencio. Para localizarlos:
--   SELECT vehicle_id, count(*) FROM stays WHERE status = 'IN_PROGRESS'
--   GROUP BY vehicle_id HAVING count(*) > 1;
CREATE UNIQUE INDEX ux_stays_one_active_per_vehicle
    ON stays (vehicle_id)
    WHERE status = 'IN_PROGRESS';


-- ---------------------------------------------------------------------------
-- 2. Token de bloqueo optimista (@Version en StayEntity).
-- ---------------------------------------------------------------------------
-- Carrera que cierra: dos check-out de la misma estancia a la vez. Los dos leen
-- la fila en IN_PROGRESS, los dos calculan importe contra tariff-service, y el
-- segundo UPDATE pisa al primero sin enterarse (lost update). Peor todavia: los
-- dos publican StayClosedEvent, asi que ticket-service emite dos tickets de
-- salida y spot-service libera la plaza dos veces (la segunda liberacion puede
-- caer sobre un coche que ya haya entrado despues).
--
-- Con esta columna, Hibernate escribe siempre:
--   UPDATE stays SET ..., version = version + 1
--   WHERE unique_id = ? AND version = ?
-- El segundo UPDATE afecta a 0 filas porque la version ya no coincide, y
-- Hibernate lanza OptimisticLockingFailureException en vez de sobrescribir.
--
-- NOT NULL con DEFAULT 0 para que las filas que ya existan entren con una
-- version valida; sin el default, el ALTER TABLE fallaria sobre tabla con datos.
ALTER TABLE stays
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;


-- ---------------------------------------------------------------------------
-- 3. Indice de apoyo para la busqueda de la estancia activa.
-- ---------------------------------------------------------------------------
-- findByVehicleIdAndStatus se ejecuta en cada check-in y en cada check-out. El
-- indice unico de arriba solo cubre las filas IN_PROGRESS; este cubre tambien
-- las consultas por otros estados sin recorrer la tabla entera.
CREATE INDEX ix_stays_vehicle_id_status
    ON stays (vehicle_id, status);
