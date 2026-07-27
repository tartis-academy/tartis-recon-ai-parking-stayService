package com.tartis_recon_ai_parking.domain.stay.exception;

public class InvalidStayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidStayException(String message) {
        super(message);
    }

    public InvalidStayException(String message, Throwable cause) {
        super(message, cause);
    }
}
