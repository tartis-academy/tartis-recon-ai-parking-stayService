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