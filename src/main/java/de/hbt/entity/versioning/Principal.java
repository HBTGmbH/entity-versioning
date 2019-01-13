package de.hbt.entity.versioning;

/**
 * The principal/user on behalf of whom entity versions are created/updated.
 *
 * <p>
 * This is being used in the {@link VersioningInterceptor} to retrieve the "current" user.
 */
public interface Principal {
  /**
   * Get the name of the current user.
   *
   * @return the name of the user
   */
  String getName();
}
