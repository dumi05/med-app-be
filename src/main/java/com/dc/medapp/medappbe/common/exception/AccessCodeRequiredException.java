package com.dc.medapp.medappbe.common.exception;

public class AccessCodeRequiredException extends RuntimeException {
    public AccessCodeRequiredException() {
        super("Serve il codice di accesso per aprire i referti.");
    }
}
