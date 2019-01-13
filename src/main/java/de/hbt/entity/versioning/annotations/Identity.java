package de.hbt.entity.versioning.annotations;

import java.lang.annotation.*;

/**
 * A field marked with this annotation is assumed to be a ManyToOne association holding the identity entity of a
 * versioned entity, or to be a simple Long field. In the case of a ManyToOne association, the associated identity
 * entity will be created once automatically and every subsequent version of the owning entity will reference the exact
 * same identity entity. In the case of a simple Long field, the value will be set once for the first version of the
 * entity and then always stays the same for all subsequent entity versions.
 *
 * <p>
 * As such, the versioning framework will ensure that once the identity has been stored on the first version of an
 * entity, it can never be set to a different identity afterwards, though the identity entity itself can be modified.
 *
 * <p>
 * If the identity is an entity, the versioning framework will also ensure that such an identity entity will always
 * exist, so it will automatically create the identity and set it on the versioned entity if it is <code>null</code>.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface Identity {
  /**
   * Specifies the name of the database sequence to use when generating simple Long identity values. The default is
   * <code>identity_seq</code>.
   *
   * @return the name of the database sequence to use when generating simple Long identity values
   */
  String sequence() default "identity_seq";
}
