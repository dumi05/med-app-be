package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.PasswordPolicy.Violation;
import com.dc.medapp.medappbe.common.exception.WeakPasswordException;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PasswordPolicyTest {

    private static final String EMAIL = "mario.rossi@clin.it";

    private final PasswordPolicy policy = new PasswordPolicy();

    private List<Violation> check(String password) {
        return policy.check(password, EMAIL, "Mario", "Rossi");
    }

    /**
     * The first {@code count} distinct emoji: 4 bytes each, one character each.
     */
    public static String emoji(int count) {
        var sb = new StringBuilder();
        IntStream.range(0, count).forEach(i -> sb.appendCodePoint(0x1F600+i));
        return sb.toString();
    }

    private static String ascii(int length) {
        var text = "tavolo-viola-nuvola-finestra-bicicletta-montagna-ruota-mela-pera-uva-limone-cedro-fico"; // random words
        return text.substring(0, length);
    }

    @Test
    void aPassphraseWithSpacesAndNoSymbolsIsAccepted() {
        assertThat(check("il mio cane dorme sul divano letto")).isEmpty();
    }

    @Test
    void noCompositionRule_aLongLowerCasePasswordIsAccepted() {
        assertThat(check("tavolo viola nuvola")).isEmpty();
    }

    @Test
    void unicodeIsAccepted() {
        assertThat(check("contraseña-ñandú-ünïcode")).isEmpty();
    }

    @Test
    void theSharedTestPasswordIsAccepted() {
        assertThat(check(ApiTestClient.PASSWORD)).isEmpty();
    }

    @Test
    void elevenCharactersAreTooShort_twelveAreEnough() {
        assertThat(check("Ks9-dse-983")).containsExactly(Violation.TOO_SHORT);
        assertThat(check("Ks9-dse-983s")).isEmpty();
    }

    @Test
    void theLengthIsCountedInCharacters_notInBytes(){
        assertThat(check(emoji(11))).containsExactly(Violation.TOO_SHORT);
        assertThat(check(emoji(12))).isEmpty();
    }

    @Test
    void seventyTwoBytesAreTheMaximum(){
        assertThat(ascii(72).getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(check(ascii(72))).isEmpty();
        assertThat(check(ascii(73))).containsExactly(Violation.TOO_LONG);
    }

    @Test
    void theMaximumIsInBytes_soEighteenEmojiPassAndNineteenDoNot(){
        assertThat(emoji(18).getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(check(emoji(18))).isEmpty();
        assertThat(check(emoji(19))).containsExactly(Violation.TOO_LONG);
    }

    @Test
    void aShortPasswordIsOnlyReportedAsShort() {
        assertThat(check("a")).containsExactly(Violation.TOO_SHORT);
    }

    @Test
    void onlySpacesIsBlank() {
        assertThat(check("            ")).containsExactly(Violation.BLANK);
        assertThat(check("")).containsExactly(Violation.BLANK);
        assertThat(check(null)).containsExactly(Violation.BLANK);
    }

    @Test
    void spacesAreNeverRemoved_aPasswordWithSpacesAtTheEndIsStillTwelveCharacters() {
        assertThat(check("tavolo viola ")).isEmpty();
    }

    // ---- common passwords -----------------------------------------------------------------------

    @Test
    void aCommonWordWithDigitsAtTheEndIsCommon() {
        assertThat(check("password1234")).containsExactly(Violation.COMMON);
        assertThat(check("Juventus2026!!")).containsExactly(Violation.COMMON);
    }

    @Test
    void theUsualSubstitutionsAreUndone() {
        assertThat(check("P@ssw0rd!2026")).containsExactly(Violation.COMMON);
        assertThat(check("F0rz@T0r1n0-2026")).contains(Violation.COMMON);
    }

    @Test
    void symbolsAndCaseDoNotHideACommonPassword() {
        assertThat(check("Forza-Torino-2026")).contains(Violation.COMMON);
        assertThat(check("I.L.O.V.E.Y.O.U.!!")).contains(Violation.COMMON);
    }

    @Test
    void aWordWrittenTwiceIsTheSamePassword() {
        assertThat(check("passwordpassword")).containsExactly(Violation.COMMON);
        assertThat(check("ciaociaociaociao")).contains(Violation.COMMON);
    }

    @Test
    void aCommonWordInsideALongerPassphraseIsAllowed() {
        assertThat(check("my-password-is-not-in-the-list-1")).isEmpty();
    }

    // ---- predictable ----------------------------------------------------------------------------

    @Test
    void oneCharacterRepeatedIsPredictable() {
        assertThat(check("aaaaaaaaaaaa")).containsExactly(Violation.PATTERN);
        assertThat(check("!!!!!!!!!!!!")).containsExactly(Violation.PATTERN);
    }

    @Test
    void aBlockRepeatedIsPredictable() {
        assertThat(check("abcabcabcabc")).containsExactly(Violation.PATTERN);
        assertThat(check("x7#x7#x7#x7#")).containsExactly(Violation.PATTERN);
    }

    @Test
    void aStretchOfASequenceIsPredictable() {
        assertThat(check("abcdefghijkl")).containsExactly(Violation.PATTERN);
        assertThat(check("lkjihgfedcba")).containsExactly(Violation.PATTERN);
        assertThat(check("123456789012")).containsExactly(Violation.PATTERN);
        assertThat(check("210987654321")).containsExactly(Violation.PATTERN);
        assertThat(check("qwertyuiopas")).containsExactly(Violation.PATTERN);
    }

    @Test
    void aSequenceInsideALongerPasswordIsAllowed() {
        assertThat(check("abcdefghijkl-tavolo-9")).isEmpty();
    }

    // ---- the data of the user -------------------------------------------------------------------

    @Test
    void thePartOfTheEmailBeforeTheAtIsRefused() {
        assertThat(check("il-mio-mario.rossi-99")).containsExactly(Violation.PERSONAL);
        assertThat(check("ilmiomariorossi99")).containsExactly(Violation.PERSONAL);
    }

    @Test
    void theNameAndTheSurnameAreRefused() {
        assertThat(policy.check("Verdi-e-viola-2026", "x@clinic.it", "Anna", "Verdi")).containsExactly(Violation.PERSONAL);
        assertThat(policy.check("anna-e-il-tavolo-7", "x@clinic.it", "Anna", "Verdi")).containsExactly(Violation.PERSONAL);
    }

    @Test
    void shortNamesAndShortPartsOfTheEmailAreIgnored() {
        assertThat(policy.check("li-tavolo-viola-nuvola", "li@clinic.it", "Li", "Wu")).isEmpty();
        assertThat(policy.check("mia-tavolo-viola-nuvola-42", "mia@clinic.it", "Mia", "Wu")).contains(Violation.PERSONAL);
        assertThat(policy.check("mi-tavolo-viola-nuvola-42", "mia@clinic.it", "Mi", "Wu")).isEmpty();
    }

    @Test
    void theNameOfTheApplicationIsRefused() {
        assertThat(check("il-mio-studiomedico-01")).containsExactly(Violation.PERSONAL);
        assertThat(check("LaMole-Torino-2026")).contains(Violation.PERSONAL);
    }

    @Test
    void missingEmailAndNamesAreHandled() {
        assertThat(policy.check(ApiTestClient.PASSWORD, null, null, null)).isEmpty();
    }

    // ---- the exception --------------------------------------------------------------------------

    @Test
    void requireValid_throwsWithAllTheReasonsInItalian() {
        var ex = assertThrows(WeakPasswordException.class, () -> policy.requireValid("corta", EMAIL, "Mario", "Rossi"));

        assertThat(ex.getMessage()).contains("almeno 12 caratteri");
    }

    @Test
    void requireValid_doesNothingForAGoodPassword() {
        assertDoesNotThrow(() -> policy.requireValid(ApiTestClient.PASSWORD, EMAIL, "Mario", "Rossi"));
    }

}
