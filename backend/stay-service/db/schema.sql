-- DDL para el perfil prod (ddl-auto=validate): sin Flyway/Liquibase todavia,
-- el esquema se crea fuera de banda. Debe reflejar exactamente StayEntity.
-- Se monta como init script en la Postgres dedicada de stay-service.

CREATE TABLE IF NOT EXISTS stays (
    unique_id    UUID PRIMARY KEY,
    vehicle_id   UUID NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    spot_id      UUID NOT NULL,
    tariff_id    UUID NOT NULL,
    check_in     TIMESTAMP WITH TIME ZONE NOT NULL,
    check_out    TIMESTAMP WITH TIME ZONE,
    total_amount NUMERIC(10,2),
    status       VARCHAR(20) NOT NULL
);
