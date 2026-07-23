package com.tartis_recon_ai_parking.domain.stay;

public enum StayStatus {

    IN_PROGRESS,

    PAY_PENDING,

    PAID,

    FINISHED,

    CANCELLED;

    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
