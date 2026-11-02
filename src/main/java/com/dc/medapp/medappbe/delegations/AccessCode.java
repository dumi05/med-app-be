package com.dc.medapp.medappbe.delegations;

import java.security.SecureRandom;
import java.util.Locale;

public class AccessCode {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccessCode() {
        // Private constructor to prevent instantiation
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            int index = RANDOM.nextInt(ALPHABET.length());
            code.append(ALPHABET.charAt(index));
        }
        return code.toString();
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String display(String code) {
        if (code == null || code.length() != LENGTH) {
            throw new IllegalArgumentException("Code must be exactly " + LENGTH + " characters long.");
        }
        return code.substring(0, 4) + "-" + code.substring(4);
    }
}
