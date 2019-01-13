package de.hbt.entity.versioning.annotations;

import java.lang.annotation.*;

/**
 * When a field is marked with this annotation, it will contain the date/time when the initial version of that entity
 * was created.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface CreationDate {
}
