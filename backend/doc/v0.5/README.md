# Alcance de la Fase I (MVP v0.5.0) — stay-service

Documento explicativo del alcance, responsabilidad, modelo de dominio, endpoints expuestos e infraestructura del microservicio `stay-service` durante la **Fase I (MVP v0.5.0)** del sistema de parking inteligente **TARTIS Recon-AI**.

---

## 1. Responsabilidad del Microservicio en Fase I

En la Fase I, `stay-service` actúa como el orquestador síncrono central del ciclo de vida de estancias de vehículos:
- **Check-in Síncrono de Vehículo:** Verificación síncrona de disponibilidad por categoría (**RN-01**), consulta de existencia de vehículo en `vehicle-service`, asignación atómica de plaza en `spot-service` (**RN-05**) e inicio de estancia en estado `IN_PROGRESS`.
- **Check-out Síncrono de Vehículo:** Recuperación de la estancia activa, cálculo síncrono del importe acumulado invocando a `tariff-service` y cierre de estancia a estado `FINISHED`.
- **Consulta de Estancias:** Listado paginado y consulta de estancias individuales por UUID.

---

## 2. Modelo de Dominio (Fase I)

Entidad principal **`Stay`** con los siguientes atributos:

| Atributo | Tipo | Descripción | Validación / Restricción |
|---|---|---|---|
| `id` | `UUID` | Identificador único universal de la estancia | Autogenerado (PK) |
| `vehiclePlate` | `String` | Matrícula del vehículo en estacionamiento | Único por estancia activa |
| `spotId` | `UUID` | Identificador de la plaza asignada | Relación con `spot-service` |
| `entryTime` | `LocalDateTime` | Fecha y hora de entrada al parking | No nulo |
| `exitTime` | `LocalDateTime` | Fecha y hora de salida del parking | Nulo en estancias activas |
| `calculatedAmount` | `BigDecimal` | Importe acumulado/calculado | Calculado en check-out |
| `status` | `StayStatus` | Estado de la estancia (`IN_PROGRESS`, `FINISHED`) | No nulo |

---

## 3. Endpoints REST Expuestos (Fase I)

| Método HTTP | Endpoint | Descripción | Cuerpo / Parámetros | Respuesta Éxito |
|---|---|---|---|---|
| `POST` | `/v1/stays/check-in` | Registro de entrada de vehículo (**RN-01**, **RN-05**) | JSON `CheckInRequest` | `201 Created` (`StayResponse`) |
| `POST` | `/v1/stays/check-out` | Cierre de estancia y cálculo síncrono | JSON `CheckOutRequest` | `200 OK` (`StayResponse`) |
| `GET` | `/v1/stays/{stayId}` | Consulta de estancia por su UUID | `{stayId}` (UUID) | `200 OK` (`StayResponse`) |
| `GET` | `/v1/stays` | Listado paginado de estancias | `page`, `size`, `status` | `200 OK` (Página de `StayResponse`) |

---

## 4. Arquitectura y Persistencia en Fase I

- **Arquitectura Hexagonal:** Adaptador REST (`StayRestControllerAdapter`), Casos de Uso (`CheckInUseCase`, `CheckOutUseCase`), Adaptadores de Salida síncronos hacia `vehicle`, `spot` y `tariff`, Adaptador de persistencia JPA (`StayPersistenceAdapter`).
- **Base de Datos:** PostgreSQL compartido `parking_dev` en puerto `5432`, esquema `stay`.

---

## 5. Diferencias Clave respecto a la Fase II (v1.0.0)

1. **Publicación Asíncrona de Eventos:** No existe el envio del evento `StayClosedEvent` a RabbitMQ.
2. **Server-Sent Events (SSE):** No existe el endpoint `GET /v1/events` (`text/event-stream`).
3. **Cancelación Manual (CB06 / RN-02):** No existe el endpoint `POST /v1/stays/{stayId}/cancel` ni el estado terminal `CANCELLED` (**IN-19**).
4. **Resiliencia & Fallbacks:** Sin cortocircuitos Resilience4j en clientes HTTP.
5. **Seguridad OAuth2 / Keycloak & Kong:** Sin verificación de tokens JWT ni autenticación de Query Parameter `?access_token=`.
