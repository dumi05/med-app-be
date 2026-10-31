package com.dc.medapp.medappbe.common.exception;

import java.util.List;

/**
 * The passwords can break one or more rules of the password policy;
 * the message lists them all, in Italian
 */
public class WeakPasswordException extends RuntimeException {
    public WeakPasswordException(List<String> reasons) {
        super(String.join(" ", reasons));
    }
}
