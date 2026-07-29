-- RECON-812: migracion baseline de Flyway, sustituye al schema.sql que se
-- montaba como init script de Postgres. Debe reflejar exactamente
-- StayEntity.
CREATE TABLE stays (
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
