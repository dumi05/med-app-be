package com.dc.medapp.medappbe.common.exception;

public class InvalidAccessCodeException extends RuntimeException {
    public InvalidAccessCodeException() {
        super("Il codice di accesso non è corretto.");
    }
}
