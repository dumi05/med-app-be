package com.dc.medapp.medappbe.common.exception;

public class InvalidCurrentPasswordException extends RuntimeException{
    public InvalidCurrentPasswordException() {
        super("La password attuale non è corretta.");
    }
}
