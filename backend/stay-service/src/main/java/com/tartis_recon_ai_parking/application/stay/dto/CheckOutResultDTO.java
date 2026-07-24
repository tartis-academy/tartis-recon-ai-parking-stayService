package com.tartis_recon_ai_parking.application.stay.dto;

import java.util.UUID;

public class CheckOutResultDTO {

	private final StayDTO stay;

	private final UUID exitTicketId;

	private final long totalMinutes;

	public CheckOutResultDTO(final StayDTO stay, final UUID exitTicketId, final long totalMinutes) {
		this.stay = stay;
		this.exitTicketId = exitTicketId;
		this.totalMinutes = totalMinutes;
	}

	public StayDTO getStay() {
		return this.stay;
	}

	public UUID getExitTicketId() {
		return this.exitTicketId;
	}

	public long getTotalMinutes() {
		return this.totalMinutes;
	}

}
