package com.dc.medapp.medappbe.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class EmailPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "mario.rossi@test.com",
            "a@b.it",
            "user+tag@sub.domain.example.org",
            "first_last%x@my-domain.co.uk",
            "UPPER.Case@Example.COM",
            "  spaced@test.it  ",
            "n0digits@x1.io"
    })
    void validAddresses(String email) {
        assertThat(EmailPolicy.isValid(email)).as(email).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "plainaddress",
            "a@b",
            "a@b.c",
            "a@b.c1",
            "a@bb",
            "mario@localhost",
            "a@@b.it",
            "a@b@c.it",
            "@b.it",
            "a@.it",
            "a@b..it",
            "a@b.it.",
            "a@-b.it",
            "a@b-.it",
            "a b@x.it",
            "a@b c.it",
            ".a@x.it",
            "a.@x.it",
            "a..b@x.it",
            "a@[127.0.0.1]",
            "\"quoted\"@x.it",
            "àb@x.it",
            "a@bücher.de",
            "a,b@x.it"
    })
    void invalidAddresses(String email) {
        assertThat(EmailPolicy.isValid(email)).as(email).isFalse();
    }

    @Test
    void nullIsNotValid() {
        assertThat(EmailPolicy.isValid(null)).isFalse();
    }

    @Test
    void theLocalPartCanHave64CharactersButNot65() {
        assertThat(EmailPolicy.isValid("a".repeat(64) + "@x.it")).isTrue();
        assertThat(EmailPolicy.isValid("a".repeat(65) + "@x.it")).isFalse();
    }

    @Test
    void theWholeAddressCanHave254CharactersButNot255() {
        var domainPrefix = ("b".repeat(63) + ".").repeat(3);
        assertThat(EmailPolicy.isValid("a@" + domainPrefix + "b".repeat(57) + ".it")).isTrue();
        assertThat(EmailPolicy.isValid("a@" + domainPrefix + "b".repeat(58) + ".it")).isFalse();
    }

    @Test
    void aLabelCanHave63CharactersButNot64() {
        assertThat(EmailPolicy.isValid("a@" + "b".repeat(63) + ".it")).isTrue();
        assertThat(EmailPolicy.isValid("a@" + "b".repeat(64) + ".it")).isFalse();
    }

    @Test
    void normalizeRemovesTheSpacesAtTheEndsAndLowersTheCase() {
        assertThat(EmailPolicy.normalize("  Mario.ROSSI@Test.COM ")).isEqualTo("mario.rossi@test.com");
        assertThat(EmailPolicy.normalize(null)).isNull();
    }
}