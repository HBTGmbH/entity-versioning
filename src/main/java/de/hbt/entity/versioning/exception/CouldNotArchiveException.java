package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class CouldNotArchiveException extends VersioningException {
  public CouldNotArchiveException(String message) {
    super(message, null);
  }
}
