package com.tartis_recon_ai_parking.application.stay.dto;

import java.util.UUID;

/**
 * Entrada del caso de uso de check-out (POST /v1/stays/check-out).
 *
 * <p>Debe llegar {@code plate} O {@code entryTicketId} (al menos uno). La matricula
 * la lee el sensor de salida y el caso de uso la resuelve a {@code vehicleId};
 * {@code entryTicketId} es el fallback cuando la matricula no se puede leer.
 */
public class StayCheckOutDTO {

	private final String plate;

	private final UUID entryTicketId;

	public StayCheckOutDTO(final String plate, final UUID entryTicketId) {
		this.plate = plate;
		this.entryTicketId = entryTicketId;
	}

	public String getPlate() {
		return this.plate;
	}

	public UUID getEntryTicketId() {
		return this.entryTicketId;
	}

}
