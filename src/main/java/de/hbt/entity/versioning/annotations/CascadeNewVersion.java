package de.hbt.entity.versioning.annotations;

import java.lang.annotation.*;

/**
 * When annotating a field, specifies whether the owning entity should get a new version when the entity or entity
 * collection referenced by the annotated field was updated and a new version of that referenced entity or collection
 * was created.
 *
 * <p>
 * When annotating a class, specifies whether the owning entity should get a new version when any of the entities or
 * entity collections referenced by any fields were updated and new versions of the updated referenced entities or
 * collections were created.
 *
 * <p>
 * When annotating a package (via package-info.java), then this has the same effect as if all classes inside that
 * package would have been annotated.
 *
 * <p>
 * This annotation is superfluous on associations that are marked as cascade PERSIST or cascade MERGE, since in that
 * case, it is assumed to be a composite for which always new versions of the owner will be created.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.TYPE, ElementType.PACKAGE })
public @interface CascadeNewVersion {

  /**
   * If <code>true</code> then a new version of the owning entity will be created whenever the referenced entity was
   * modified and a new version of that entity was created.
   *
   * <p>
   * The default is <code>true</code>.
   *
   * @return whether a new version of the owning entity should be created
   */
  boolean value() default true;

  /**
   * If <code>true</code> and {@link #value()} is <code>true</code>, then the new version of the owning entity will be
   * marked deleted whenever the referenced entity was also marked as deleted. This only applies to ManyToOne
   * associations.
   *
   * @return whether a new version of the owning entity will be marked as deleted iff the referenced entity is deleted
   *         as well
   */
  boolean withDelete() default false;
}
