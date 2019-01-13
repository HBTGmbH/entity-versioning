package de.hbt.entity.versioning;

import java.io.*;

/**
 * Can be implemented by Spring beans to get notified about versioning/lifecycle events on JPA entities.
 */
public interface VersioningListener {

  /**
   * Will be called by the versioning interceptor whenever the given entity has been archived.
   *
   * @param entity the entity object
   * @param id     the identifier of that entity
   */
  void onEntityArchived(Object entity, Serializable id);
}
