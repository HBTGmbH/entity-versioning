package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class ModifiedArchivedException extends VersioningException {
  public ModifiedArchivedException(String message) {
    super(message, null);
  }
}
