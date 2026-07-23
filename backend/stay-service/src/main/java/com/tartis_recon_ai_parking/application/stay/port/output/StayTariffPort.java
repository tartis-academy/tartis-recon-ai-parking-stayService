package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface StayTariffPort {

    /**
     * Obtiene la tarifa activa para asociarla a la estancia durante el check-in.
     */
    UUID getActiveTariffId(VehicleType vehicleType);

    /**
     * Pide a tariff-service que calcule el importe consumido entre dos fechas.
     */
    BigDecimal calculateAmount(UUID tariffId, Instant checkIn, Instant checkOut);
}