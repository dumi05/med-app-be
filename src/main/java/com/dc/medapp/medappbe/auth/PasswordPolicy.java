package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.common.exception.WeakPasswordException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * These rules are retrieved from OWASP ASVS (v2.1) and NIST SP 800-63B.
 */
@Component
public class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private static final int MAX_BLOCK = 6;
    private static final int MIN_EMAIL_PART = 4;
    private static final int MIN_NAME_PART = 3;
    private static final List<String> CONTEXT_WORDS = List.of("lamole", "studiomedico");
    private static final List<String> SEQUENCES = sequences();

    public enum Violation {
        TOO_SHORT("La password deve avere almeno " + MIN_LENGTH + " caratteri."),
        TOO_LONG("La password può avere al massimo " + MAX_BYTES + " byte (circa " + MAX_BYTES + " caratteri: i caratteri speciali ne occupano di più)."),
        BLANK("La password non può contenere solo spazi."),
        COMMON("Questa password è troppo comune."),
        PATTERN("La password è troppo prevedibile (ripetizioni o sequenze)."),
        PERSONAL("La password non deve contenere l'email, il nome o il cognome.");

        private final String message;

        Violation(String message) {
            this.message = message;
        }
        public String getMessage() {
            return message;
        }
    }

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        commonPasswords = loadCommonPasswords();
    }

    public List<Violation> check(String password, String email, String firstName, String lastName) {
        if(password == null || password.isEmpty() || password.isBlank()){
            return List.of(Violation.BLANK);
        }
        var violations = new ArrayList<Violation>();
        if(password.codePointCount(0, password.length()) < MIN_LENGTH){
            violations.add(Violation.TOO_SHORT);
        }
        if(password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES){
            violations.add(Violation.TOO_LONG);
        }
        if(!violations.isEmpty()){
            return violations;
        }
        if(isCommon(password)){
            violations.add(Violation.COMMON);
        }

        if(isPredictable(password)){
            violations.add(Violation.PATTERN);
        }
        if(containsPersonalData(password, email, firstName, lastName)){
            violations.add(Violation.PERSONAL);
        }
        return violations;
    }

    public void requireValid(String password, String email, String firstName, String lastName) {
        var violations = check(password, email, firstName, lastName);
        if(!violations.isEmpty()){
            throw new WeakPasswordException(violations.stream().map(Violation::getMessage).toList());
        }
    }

    private boolean isCommon(String password){
        var plain = plain(password);
        var forms = new ArrayList<String>(List.of(plain, stripTrailingDigits(plain)));
        forms.addAll(leetForms(password));
        for (String form : forms) {
            if(form.isEmpty()){
                continue;
            }
            if(commonPasswords.contains(form) || isRepetitionOfCommon(form)){
                return true;
            }
        }
        return false;
    }

    private boolean isRepetitionOfCommon(String form){
        for(var word: commonPasswords) {
            if(form.length() > word.length() && form.length() % word.length() == 0
                && form.equals(word.repeat(form.length()/word.length()))) {
                return true;
            }
        }
        return false;
    }

    private static String plain(String value) {
        var builder = new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static String stripTrailingDigits(String value) {
        int end = value.length() ;
        while(end > 0 && Character.isDigit(value.charAt(end-1))){
            end--;
        }
        return value.substring(0,end);
    }

    /**
     * The usual substitutions undone ("P@ssw0rd!2026" becomes "password"), keeping only the letters a-z. People add
     * digits and symbols at the end ("2026"), which are not a "o" or an "e" in disguise, but the last "0" of "F0rz@T0r1n0"
     * is: so every prefix is tried, as long as what is cut off is made of digits and symbols only.
     * @param password
     * @return List of strings
     */
    private static List<String> leetForms(String password){
        var lowerCase = password.toLowerCase(Locale.ROOT);
        var forms = new ArrayList<String>();
        for(int end = lowerCase.length(); end > 0; end--){
            forms.add(leet(lowerCase.substring(0,end)));
            if(Character.isLetter(lowerCase.charAt(end-1))){
                break;
            }
        }
        return forms;
    }

    private static String leet(String input){
        var builder = new StringBuilder();
        for (char c : input.toCharArray()) {
            char mapped = switch (c) {
                case '@' , '4' -> 'a';
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '$' , '5' -> 's';
                case '7' -> 't';
                default -> c;
            };
            if(mapped >= 'a' && mapped <= 'z')
                builder.append(mapped);
        }
        return builder.toString();
    }

    private static boolean isPredictable(String password) {
        var lower = password.toLowerCase(Locale.ROOT);
        return isOneCharacterRepeated(lower) || isBlockRepeated(lower) || isRunOfASequence(lower);
    }

    private static boolean isOneCharacterRepeated(String val) {
        return val.codePoints().distinct().count() == 1;
    }

    private static boolean isBlockRepeated(String value){
        for(var size = 1; size <= MAX_BLOCK; size++){
            if(value.length() % size == 0 && value.length() / size >= 2
                && value.equals(value.substring(0, size).repeat(value.length()/size))){
                return true;
            }
        }
        return false;
    }

    private static boolean isRunOfASequence(String value) {
        return SEQUENCES.stream().anyMatch(s -> s.contains(value));
    }

    private static List<String> sequences() {
        var bases = List.of("abcdefghijklmnopqrstuvwxyz", "qwertyuiopasdfghjklzxcvbnm", "01234567890123456789");
        var all = new ArrayList<>(bases);
        bases.forEach(base -> all.add(new StringBuilder(base).reverse().toString()));
        return List.copyOf(all);
    }

    private static boolean containsPersonalData(String password, String email, String firstName, String lastName) {
        var plain = plain(password);
        var tokens = new ArrayList<>(CONTEXT_WORDS);
        if(email != null && email.contains("@")){
            addIfLongEnough(tokens, plain(email.substring(0, email.indexOf('@'))), MIN_EMAIL_PART);
        }
        addIfLongEnough(tokens, firstName == null ? "" : plain(firstName), MIN_NAME_PART);
        addIfLongEnough(tokens, lastName == null ? "" : plain(lastName), MIN_NAME_PART);
        return tokens.stream().anyMatch(plain::contains);
    }

    private static void addIfLongEnough(List<String> tokens, String token, int minLength){
        if(token.length() >= minLength) {
            tokens.add(token);
        }
    }

    private static Set<String> loadCommonPasswords() {
        var resource = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt");
        if(resource == null){
            throw new IllegalStateException("common-passwords.txt is missing from the classpath");
        }
        try(var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))){
            var words = new HashSet<String>();
            String line;
            while((line = reader.readLine()) != null){
                var word = line.trim().toLowerCase(Locale.ROOT);
                if(!word.isEmpty() && !word.startsWith("#")) {
                    words.add(word);
                }
            }
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read common-passwords.txt",e);
        }
    }
}
