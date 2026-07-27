package com.tartis_recon_ai_parking.application.stay.dto;

public class CheckInResultDTO {

	private final StayDTO stay;

	private final EntryTicketDTO entryTicket;

	public CheckInResultDTO(final StayDTO stay, final EntryTicketDTO entryTicket) {
		this.stay = stay;
		this.entryTicket = entryTicket;
	}

	public StayDTO getStay() {
		return this.stay;
	}

	public EntryTicketDTO getEntryTicket() {
		return this.entryTicket;
	}

}
