package com.dc.medapp.medappbe.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method name whose every call (successful or not) must
 * be written in the access log.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * what is being done (e.g. {@code READ_REPORT})
     */
    String action();

    /**
     * On which kind of record (e.g. {@code MedicalReport})
     * @return entity
     */
    String entity();

    /**
     * Index of the method argument holding the id of the record
     * the caller was trying to reach.
     * Used when the call fails or does not return an entity. {@code -1}: none
     * @return entityId
     */
    int idArg() default -1;
}
