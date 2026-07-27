package com.tartis_recon_ai_parking.application.stay.dto;

import java.time.Instant;
import java.util.UUID;

public class EntryTicketDTO {

	private final UUID ticketId;

	private final String barCode;

	private final Instant issuedAt;

	public EntryTicketDTO(final UUID ticketId, final String barCode, final Instant issuedAt) {
		this.ticketId = ticketId;
		this.barCode = barCode;
		this.issuedAt = issuedAt;
	}

	public UUID getTicketId() {
		return this.ticketId;
	}

	public String getBarCode() {
		return this.barCode;
	}

	public Instant getIssuedAt() {
		return this.issuedAt;
	}

}
