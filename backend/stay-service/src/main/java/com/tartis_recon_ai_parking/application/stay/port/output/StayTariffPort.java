package com.tartis_recon_ai_parking.application.stay.port.output;

import com.tartis_recon_ai_parking.domain.stay.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;

public interface StayTariffPort {

    /**
     * Obtiene la tarifa activa para asociarla a la estancia durante el check-in.
     */
    UUID getActiveTariffId(VehicleType vehicleType);

    /**
     * Pide a tariff-service el importe de la estancia. El calculo (RN-07 a RN-09)
     * es responsabilidad de tariff; stay solo aporta el tipo de vehiculo y los
     * minutos ya redondeados a su favor (RN-06).
     */
    BigDecimal calculateAmount(VehicleType vehicleType, long totalMinutes);
}
