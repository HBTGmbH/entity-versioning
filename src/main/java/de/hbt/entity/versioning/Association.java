package de.hbt.entity.versioning;

import java.lang.reflect.*;

import de.hbt.entity.versioning.exception.*;
import lombok.*;

/**
 * Models an association between two entities.
 *
 * <p>
 * <em>This class is only to be used by the classes inside the {@link de.hbt.entity.versioning} package and
 * sub-packages!</em>
 */
@Getter
@Setter
class Association {

  public enum Kind {
    SINGULAR, PLURAL;
  }

  private boolean shouldCascadeNewVersion;
  private boolean shouldCascadeDelete;
  private Kind kind;
  private Class<?> owner;
  private String name;
  private @Getter(value = AccessLevel.PRIVATE) Member getter;
  private @Getter(value = AccessLevel.PRIVATE) Member setter;

  /** Set the association value on the given owner via the setter member. */
  void write(Object owner, Object value) {
    if (setter instanceof Field) {
      /*
       * The Field has already been made accessible by Hibernate!
       */
      try {
        ((Field) setter).set(owner, value);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new InternalVersioningException("Could not access setter", e);
      }
    } else if (setter instanceof Method) {
      try {
        ((Method) setter).invoke(owner, value);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new InternalVersioningException("Could not access setter", e);
      }
    } else {
      throw new IllegalArgumentException("Unexpected Java member [" + setter + "] for assocation setter ["
          + owner.getClass().getSimpleName() + "." + name + "]");
    }
  }

  /** Get the association value from the given owner via the getter member. */
  Object read(Object owner) {
    if (getter instanceof Field) {
      /*
       * The Field has already been made accessible by Hibernate!
       */
      try {
        return ((Field) getter).get(owner);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new InternalVersioningException("Could not access getter", e);
      }
    } else if (getter instanceof Method) {
      try {
        return ((Method) getter).invoke(owner);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new InternalVersioningException("Could not access getter", e);
      }
    } else {
      /*
       * We currently do not support methods since there is no standard way in
       */
      throw new IllegalArgumentException("Unexpected Java member [" + getter + "] for assocation getter ["
          + owner.getClass().getSimpleName() + "." + name + "]");
    }
  }
}
