package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class InternalVersioningException extends VersioningException {
  public InternalVersioningException(String message, Throwable cause) {
    super(message, cause);
  }
}
