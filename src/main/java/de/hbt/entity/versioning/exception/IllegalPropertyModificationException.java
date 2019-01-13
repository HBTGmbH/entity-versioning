package de.hbt.entity.versioning.exception;

/** Thrown when an entity property was illegally modified. */
@SuppressWarnings("serial")
public class IllegalPropertyModificationException extends VersioningException {
  public IllegalPropertyModificationException(String message) {
    super(message, null);
  }
}
