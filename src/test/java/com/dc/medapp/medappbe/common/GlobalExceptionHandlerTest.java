package com.dc.medapp.medappbe.common;

import com.dc.medapp.medappbe.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    public void handleSlotNonDisponibile_return409(){
        var response = handler.handleSlotNonDisponibileException(new SlotNonDisponibileException("occupato"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codice()).isEqualTo("SLOT_NON_DISPONIBILE");
    }

    @Test
    public void handleTransizioneNonValida_return409(){
        var response = handler.handleTransizioneStatoNonValidaException(new TransizioneStatoNonValidaException("stato errato"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codice()).isEqualTo("TRANSIZIONE_NON_VALIDA");
    }

    @Test
    public void handleAccessoNonAutorizzato_return403(){
        var response = handler.handleAccessoNonAutorizzatoException(new AccessoNonAutorizzatoException("accesso negato"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codice()).isEqualTo("ACCESSO_NON_AUTORIZZATO");
    }

    @Test
    public void handleRisorsaNonTrovata_return404(){
        var response = handler.handleRisorsaNonTrovataException(new RisorsaNonTrovataException("risorsa non trovata"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codice()).isEqualTo("RISORSA_NON_TROVATA");
    }

    @Test
    public void handleIllegalArgument_return400(){
        var response = handler.handleIllegalArgumentException(new IllegalArgumentException("argomento non valido"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codice()).isEqualTo("ARGOMENTO_NON_VALIDO");
    }
}
