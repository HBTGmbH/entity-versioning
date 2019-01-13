package de.hbt.entity.versioning.exception;

/** Thrown to indicate that a persisted entity */
@SuppressWarnings("serial")
public class UnexpectedVersionException extends VersioningException {
  public UnexpectedVersionException(String message) {
    super(message, null);
  }
}
