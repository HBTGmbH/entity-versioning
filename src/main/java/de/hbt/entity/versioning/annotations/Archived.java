package de.hbt.entity.versioning.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;

/**
 * When a field is marked with this annotation, it will hold a boolean indicating whether that entity version is the
 * most current version. The field's value will be <code>false</code> when it is the most current version and
 * <code>true</code> when it is an old/archived version.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface Archived {
}
