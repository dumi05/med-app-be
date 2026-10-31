package com.dc.medapp.medappbe.common.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailPolicy {

    public static final int MAX_LENGTH = 254;
    public static final int MAX_LOCAL_LENGTH = 64;

    private static final Pattern LOCAL_PART = Pattern.compile("[A-Za-z0-9._%+-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern LAST_LABEL = Pattern.compile("[A-Za-z]{2,}");

    private EmailPolicy() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String email) {
        var value = normalize(email);
        if(value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        int at = value.indexOf('@');
        if(at <= 0 || at != value.lastIndexOf('@')){
            return false;
        }
        var local = value.substring(0, at);
        var domain = value.substring(at + 1);
        if(local.length() > MAX_LOCAL_LENGTH || !LOCAL_PART.matcher(local).matches()) {
            return false;
        }
        if(local.startsWith(".") || local.endsWith(".") || local.contains("..")){
            return false;
        }
        var labels = domain.split("\\.", -1);
        if(labels.length < 2){
            return false;
        }
        for (String label : labels) {
            if(!DOMAIN_LABEL.matcher(label).matches()){
                return false;
            }
        }
        return LAST_LABEL.matcher(labels[labels.length-1]).matches();
    }
}
