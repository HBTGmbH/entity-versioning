package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class ModifiedUnexpectedVersionException extends VersioningException {
  public ModifiedUnexpectedVersionException(String message) {
    super(message, null);
  }
}
