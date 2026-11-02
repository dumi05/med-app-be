package com.dc.medapp.medappbe.common.exception;

public class SpecializationAlreadyExistsException extends RuntimeException{
    public SpecializationAlreadyExistsException(String name) {
        super("La specializzazione esiste già: " + name);
    }
}
