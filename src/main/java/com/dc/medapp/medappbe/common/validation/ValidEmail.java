package com.dc.medapp.medappbe.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidEmailValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {
    String message() default "Inserisci un indirizzo email valido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
