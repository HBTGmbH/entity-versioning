package de.hbt.entity.versioning.annotations;

import java.lang.annotation.*;

/**
 * When a field is marked with this annotation, it will contain the date/time of modification of that entity.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface ModificationDate {
}
