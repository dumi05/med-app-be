package com.dc.medapp.medappbe.common;

import com.dc.medapp.medappbe.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    public void handleSlotNotAvailable_return409(){
        var response = handler.handleSlotNotAvailableException(new SlotNotAvailableException("occupato"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SLOT_NOT_AVAILABLE");
    }

    @Test
    public void handleStateTransitionNotValid_return409(){
        var response = handler.handleInvalidStateTransitionException(new InvalidStateTransitionException("stato errato"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    public void handleAccessNotAuthorized_return403(){
        var response = handler.handleUnauthorizedAccessException(new UnauthorizedAccessException("accesso negato"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED_ACCESS");
    }

    @Test
    public void handleResourceNotFound_return404(){
        var response = handler.handleResourceNotFoundException(new ResourceNotFoundException("risorsa non trovata"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    public void handleIllegalArgument_return400(){
        var response = handler.handleIllegalArgumentException(new IllegalArgumentException("argomento non valido"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void handleOptimisticLockingFailure_return409(){
        var response = handler.handleOptimisticLockingFailureException(new OptimisticLockingFailureException("versione obsoleta"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void handleFileTooLargeException_returns413() {
        var response = handler.handleFileTooLargeException(new FileTooLargeException("troppo grande"));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void handleFileTooLarge_alsoAnswerForTheLimitOfTheServer() {
        var response = handler.handleFileTooLargeException(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void handleWeakPassword_returns400WithTheReasons() {
        var response = handler.handleWeakPasswordException(new WeakPasswordException(List.of("Prima.", "Seconda.")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("WEAK_PASSWORD");
        assertThat(response.getBody().message()).isEqualTo("Prima. Seconda.");
    }

    @Test
    void handleInvalidCurrentPassword_returns400() {
        var response = handler.handleInvalidCurrentPasswordException(new InvalidCurrentPasswordException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CURRENT_PASSWORD");
    }

    @Test
    void handleAccountLocked_returns429WithRetryAfter_roundedUp() {
        var response = handler.handleAccountLockedException(new AccountLockedException(Duration.ofMillis(1500)));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void handleAccountLocked_neverAsksToWaitLessThanASecond() {
        var response = handler.handleAccountLockedException(new AccountLockedException(Duration.ofMillis(100)));

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
    }

    @Test
    void handleAccountLocked_saysTheMinutesInItalian() {
        assertThat(handler.handleAccountLockedException(new AccountLockedException(Duration.ofSeconds(60))).getBody().message())
                .endsWith("1 minuto.");
        assertThat(handler.handleAccountLockedException(new AccountLockedException(Duration.ofSeconds(61))).getBody().message())
                .endsWith("2 minuti.");
    }

    @Test
    void handleEmailAlreadyInUse_returns409() {
        var response = handler.handleEmailAlreadyInUseException(new EmailAlreadyInUseException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_IN_USE");
        assertThat(response.getBody().message()).isEqualTo("Questa email è già registrata.");
    }

    @Test
    void handleSpecializationAlreadyExists_returns409() {
        var response = handler.handleSpecializationAlreadyExistsException(new SpecializationAlreadyExistsException("Cardiologia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("SPECIALIZATION_ALREADY_EXISTS");
        assertThat(response.getBody().message()).contains("Cardiologia");
    }
}
