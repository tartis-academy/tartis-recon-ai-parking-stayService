package com.tartis_recon_ai_parking.application.stay.dto;

import java.util.UUID;
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
