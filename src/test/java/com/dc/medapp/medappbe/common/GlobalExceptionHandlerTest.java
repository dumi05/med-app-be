package com.dc.medapp.medappbe.common;

import com.dc.medapp.medappbe.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
}
