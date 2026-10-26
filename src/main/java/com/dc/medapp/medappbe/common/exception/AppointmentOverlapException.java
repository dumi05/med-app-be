package com.dc.medapp.medappbe.common.exception;

public class AppointmentOverlapException extends RuntimeException {
    public AppointmentOverlapException(String message) {
        super(message);
    }
}
