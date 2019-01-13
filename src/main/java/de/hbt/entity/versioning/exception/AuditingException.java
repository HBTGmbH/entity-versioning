package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class AuditingException extends VersioningException {
  public AuditingException(String message, Throwable cause) {
    super(message, cause);
  }
}
