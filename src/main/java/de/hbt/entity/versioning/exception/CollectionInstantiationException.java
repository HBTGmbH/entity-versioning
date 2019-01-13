package de.hbt.entity.versioning.exception;

@SuppressWarnings("serial")
public class CollectionInstantiationException extends VersioningException {
  public CollectionInstantiationException(String message) {
    super(message, null);
  }
}
