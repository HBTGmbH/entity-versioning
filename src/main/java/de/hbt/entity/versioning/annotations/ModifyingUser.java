package de.hbt.entity.versioning.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;

/**
 * When a field is marked with this annotation, it will contain the user/principal which modified the version or created
 * the first version if the entity is the first version.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface ModifyingUser {
}
