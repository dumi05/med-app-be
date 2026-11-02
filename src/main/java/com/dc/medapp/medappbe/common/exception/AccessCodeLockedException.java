package com.dc.medapp.medappbe.common.exception;

import java.time.Duration;

public class AccessCodeLockedException extends RuntimeException {

    private final Duration remaining;

    public AccessCodeLockedException(final Duration remaining) {
        super("Troppi codici sbagliati");
        this.remaining = remaining;
    }

    public long retryAfterSeconds() {
        return Math.max(1, remaining.getSeconds() + (remaining.toNanosPart() > 0 ? 1 : 0));
    }
}
