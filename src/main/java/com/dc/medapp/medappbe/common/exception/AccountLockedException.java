package com.dc.medapp.medappbe.common.exception;

import java.time.Duration;

public class AccountLockedException extends RuntimeException{

    private final Duration remaining;

    public AccountLockedException(final Duration remaining) {
        super("Troppi tentativi di accesso falliti.");
        this.remaining = remaining;
    }

    public long retryAfterSeconds() {
        return Math.max(1, remaining.toSeconds()+ (remaining.toNanosPart() > 0 ? 1 : 0));
    }
}
