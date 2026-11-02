package com.dc.medapp.medappbe.common.exception;

import com.dc.medapp.medappbe.common.ErrorResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStateTransitionException(InvalidStateTransitionException ex) {
        return build(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(SlotNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleSlotNotAvailableException(SlotNotAvailableException ex) {
        return build(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccessException(UnauthorizedAccessException ex) {
        return build(HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "La risorsa è stata modificata da un altro utente, riprova.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getFieldErrors().stream().map(err -> err.getField()+": "+err.getDefaultMessage()).collect(Collectors.joining("; ")));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenziali non valide.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", "Accesso negato..");
    }

    @ExceptionHandler(SlotOverlapException.class)
    public ResponseEntity<ErrorResponse> handleSlotOverlapException(SlotOverlapException ex) {
        return build(HttpStatus.CONFLICT, "SLOT_OVERLAP", ex.getMessage());
    }

    @ExceptionHandler(AppointmentOverlapException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentOverlapException(AppointmentOverlapException ex) {
        return build(HttpStatus.CONFLICT, "APPOINTMENT_OVERLAP", ex.getMessage());
    }

    @ExceptionHandler({FileTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleFileTooLargeException(Exception ex) {
        return build(HttpStatus.valueOf(413), "FILE_TOO_LARGE", "Il file supera la dimensione massima di 10 MB");
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWeakPasswordException(WeakPasswordException ex) {
        return build(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLockedException(AccountLockedException ex){
        long seconds = ex.retryAfterSeconds();
        long minutes = (seconds+59)/60;
        var message = "Troppi tentativi di accesso falliti. Riprova tra " + minutes + (minutes == 1 ? " minuto." : " minuti.");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(new ErrorResponse("ACCOUNT_LOCKED", message, Instant.now()));
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCurrentPasswordException(InvalidCurrentPasswordException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyInUseException(EmailAlreadyInUseException ex) {
        return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", ex.getMessage());
    }

    @ExceptionHandler(SpecializationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleSpecializationAlreadyExistsException(SpecializationAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, "SPECIALIZATION_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(InvalidAccessCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccessCodeException(InvalidAccessCodeException ex) {
        return build(HttpStatus.FORBIDDEN, "INVALID_ACCESS_CODE", ex.getMessage());
    }

    @ExceptionHandler(AccessCodeLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccessCodeLockedException(AccessCodeLockedException ex) {
        long seconds = ex.retryAfterSeconds();
        long minutes = (seconds+59)/60;
        var message = "Troppi codici sbagliati. Riprova tra " + minutes + (minutes == 1 ? " minuto." : " minuti.");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(new ErrorResponse("ACCESS_CODE_LOCKED", message, Instant.now()));
    }

    @ExceptionHandler(AccessCodeRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAccessCodeRequiredException(AccessCodeRequiredException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_CODE_REQUIRED", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus httpStatus, String code, String message) {
        return ResponseEntity.status(httpStatus).body(new ErrorResponse(code, message, Instant.now()));
    }

}
