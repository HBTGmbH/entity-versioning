package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class VersioningException extends RuntimeException {
  public VersioningException(String message, Throwable cause) {
    super(message, cause);
  }
}
