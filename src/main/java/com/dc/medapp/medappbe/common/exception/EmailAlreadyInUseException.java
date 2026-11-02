package com.dc.medapp.medappbe.common.exception;

public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException() {
        super("Questa email è già registrata.");
    }
}
