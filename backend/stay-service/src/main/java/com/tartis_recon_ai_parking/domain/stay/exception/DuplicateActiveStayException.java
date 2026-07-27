package com.tartis_recon_ai_parking.domain.stay.exception;
 
/**
 * CB-05 / IN-02: se lanza cuando se intenta hacer check-in de una matricula
 * que ya tiene una estancia en curso. El sistema debe denegar el acceso
 * automaticamente para prevenir fraudes.
 */
public class DuplicateActiveStayException extends RuntimeException {
 
    public DuplicateActiveStayException(String message) {
        super(message);
    }
}