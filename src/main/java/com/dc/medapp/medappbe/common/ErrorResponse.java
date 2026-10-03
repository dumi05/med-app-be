package com.dc.medapp.medappbe.common;

import java.time.Instant;

public record ErrorResponse(String codice, String messaggio, Instant timestamp) {
}
