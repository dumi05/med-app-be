package com.dc.medapp.medappbe.delegations;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessCodeTest {

    @Test
    void generate_makesEightCharactersFromTheAlphabet() {
        for(int i = 0; i< 200; i++) {
            String code = AccessCode.generate();
            assertThat(code.length()).isEqualTo(8);
            assertThat(code.chars()).allMatch(c -> AccessCode.ALPHABET.indexOf(c) >= 0);
        }
    }

    @Test
    void theAlphabet_hasNoSignsThatAreEasyToConfuse() {
        assertThat(AccessCode.ALPHABET).hasSize(31).doesNotContain("I", "L", "O", "1", "0");
    }

    @Test
    void generate_doesNotRepeatItself() {
        var codes = new HashSet<String>();
        for(int i = 0; i < 200; i++) {
            codes.add(AccessCode.generate());
        }
        assertThat(codes).hasSize(200);
    }

    @Test
    void normalize_ignoresCaseDashesAndSpaces() {
        assertThat(AccessCode.normalize("aBcDeF12")).isEqualTo("ABCDEF12");
        assertThat(AccessCode.normalize("a-b-c-d-e-f-1-2")).isEqualTo("ABCDEF12");
        assertThat(AccessCode.normalize("a b c d e f 1 2")).isEqualTo("ABCDEF12");
        assertThat(AccessCode.normalize(null)).isEmpty();
    }

    @Test
    void display_putsADashInTheMiddle() {
        assertThat(AccessCode.display("ABCDEF12")).isEqualTo("ABCD-EF12");
    }
}
