package de.hbt.entity.versioning.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;

/**
 * When a field is marked with this annotation, it will make that entity type a versioned entity and it will hold the
 * version number of a versioned entity.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface Version {
}
