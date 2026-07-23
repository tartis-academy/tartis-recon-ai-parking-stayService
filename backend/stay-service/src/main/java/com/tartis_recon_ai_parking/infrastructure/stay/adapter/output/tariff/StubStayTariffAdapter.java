package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.tariff;

import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.domain.stay.VehicleType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementacion <b>provisional</b> de {@link StayTariffPort}.
 *
 * <p>El check-in no puede crear una estancia sin tarifa: IN-13 exige que toda
 * estancia referencie una tarifa existente. Hoy ningun adaptador implementa este
 * puerto —{@code StayTariffClientAdapter} de la PR de puertos no lleva
 * {@code implements StayTariffPort}—, asi que sin este stub el contexto de Spring
 * no arranca. Devuelve un id fijo configurable para no bloquear el flujo de entrada.
 *
 * <p>TODO sustituir por el adaptador REST real contra tariff-service
 * ({@code GET /v1/tariffs/active?type=}) cuando implemente el puerto. El caso de
 * uso no se entera: solo conoce {@link StayTariffPort}. Cuando exista el bean real,
 * borrar este stub para no tener dos beans del mismo puerto.
 *
 * <p><b>No usar en produccion</b>: el id no corresponde a ninguna tarifa real.
 */
@Component
public class StubStayTariffAdapter implements StayTariffPort {

    private final UUID tariffId;

    public StubStayTariffAdapter(
            @Value("${services.tariff.stub-id:00000000-0000-0000-0000-000000000001}") UUID tariffId) {
        this.tariffId = tariffId;
    }

    @Override
    public UUID getActiveTariffId(VehicleType vehicleType) {
        return tariffId;
    }

    @Override
    public BigDecimal calculateAmount(UUID tariffId, Instant checkIn, Instant checkOut) {
        // El calculo del importe es cosa del check-out (RN-06 a RN-09), fuera del
        // alcance del check-in. Se implementara con el adaptador real de tariff.
        throw new UnsupportedOperationException(
                "calculateAmount no esta implementado en el stub de tarifas (pertenece al check-out)");
    }
}
