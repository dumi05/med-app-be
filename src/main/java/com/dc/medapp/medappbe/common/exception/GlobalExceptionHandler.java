package com.dc.medapp.medappbe.common.exception;

import com.dc.medapp.medappbe.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransizioneStatoNonValidaException.class)
    public ResponseEntity<ErrorResponse> handleTransizioneStatoNonValidaException(TransizioneStatoNonValidaException ex) {
        return build(HttpStatus.CONFLICT, "TRANSIZIONE_NON_VALIDA", ex.getMessage());
    }

    @ExceptionHandler(RisorsaNonTrovataException.class)
    public ResponseEntity<ErrorResponse> handleRisorsaNonTrovataException(RisorsaNonTrovataException ex) {
        return build(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA", ex.getMessage());
    }

    @ExceptionHandler(SlotNonDisponibileException.class)
    public ResponseEntity<ErrorResponse> handleSlotNonDisponibileException(SlotNonDisponibileException ex) {
        return build(HttpStatus.CONFLICT, "SLOT_NON_DISPONIBILE", ex.getMessage());
    }

    @ExceptionHandler(AccessoNonAutorizzatoException.class)
    public ResponseEntity<ErrorResponse> handleAccessoNonAutorizzatoException(AccessoNonAutorizzatoException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCESSO_NON_AUTORIZZATO", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "ARGOMENTO_NON_VALIDO", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus httpStatus, String codice, String messaggio) {
        return ResponseEntity.status(httpStatus).body(new ErrorResponse(codice, messaggio, Instant.now()));
    }
}
