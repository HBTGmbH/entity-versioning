package de.hbt.entity.versioning;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.persistence.*;

import org.hibernate.persister.entity.*;

import lombok.*;

/** hashCode/equals via (type, id) equality. */
@Data
@NoArgsConstructor
@AllArgsConstructor
class EntityKey {
  Class<? extends Object> type;
  Serializable id;
}

/** Reference to a entity using identity hashCode and object == identity. */
class Ref {
  Object referee;

  static Ref of(Object e) {
    Ref ref = new Ref();
    ref.referee = e;
    return ref;
  }

  public int hashCode() {
    return System.identityHashCode(referee);
  }

  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof Ref))
      return false;
    Ref ref = (Ref) obj;
    return this.referee == ref.referee;
  }
}

/** Holds information about old and new versions of entities and old/new versions of collections. */
class VersioningContext {
  Map<EntityKey, Object> versionsToArchive = new HashMap<>();
  Map<EntityKey, EntityKey> newVersionMapping = new HashMap<>();
  Map<EntityKey, Ref> versionsToPersist = new HashMap<>();
  Map<Object, Object> handledCollections = new IdentityHashMap<>();

  /** Key for caching (entity Class, property name) -> property index */
  @AllArgsConstructor
  static @Data class EntityProperty {
    Class<?> entityClass;
    String propertyName;
  }

  /** (entity Class, property name) -> property index */
  private static final Map<EntityProperty, Integer> ENTITY_PROPERTY_NAME_INDICES_CACHE = new ConcurrentHashMap<>();

  /**
   * Find the index of the property with the given name inside of the properties managed by the given
   * {@link EntityPersister}.
   *
   * @param ep   the {@link EntityPersister} managing the properties of an entity class
   * @param name the name of the searched property
   * @return the index of the name or -1 if not found
   */
  static int findPropertyIndex(EntityPersister ep, String name) {
    EntityProperty key = new EntityProperty(ep.getMappedClass(), name);
    Integer propertyIndex = ENTITY_PROPERTY_NAME_INDICES_CACHE.get(key);
    if (propertyIndex != null)
      return propertyIndex.intValue();
    String[] names = ep.getPropertyNames();
    int index = -1;
    for (int i = 0; i < names.length; i++)
      if (name.equals(names[i])) {
        index = i;
        break;
      }
    ENTITY_PROPERTY_NAME_INDICES_CACHE.put(key, Integer.valueOf(index));
    return index;
  }

  /**
   * Find the index of the property with the given name inside of the given properties array.
   *
   * @param clazz the entity class
   * @param names the entity's property names
   * @param name  the name of the searched property
   * @return the index of the name or -1 if not found
   */
  static int findPropertyIndex(Class<?> clazz, String[] names, String name) {
    EntityProperty key = new EntityProperty(clazz, name);
    Integer propertyIndex = ENTITY_PROPERTY_NAME_INDICES_CACHE.get(key);
    if (propertyIndex != null)
      return propertyIndex.intValue();
    int index = -1;
    for (int i = 0; i < names.length; i++)
      if (name.equals(names[i])) {
        index = i;
        break;
      }
    ENTITY_PROPERTY_NAME_INDICES_CACHE.put(key, Integer.valueOf(index));
    return index;
  }

  /** Determine whether the given entity is a new version of some old entity. */
  boolean isNewVersion(Object e, MetaModel metaModel) {
    EntityKey key = new EntityKey(e.getClass(), metaModel.getIdOf(e));
    if (newVersionMapping.containsValue(key))
      return true;
    return versionsToPersist.containsValue(Ref.of(e));
  }

  boolean isNewVersion(Object e, EntityKey key) {
    if (newVersionMapping.containsValue(key))
      return true;
    return versionsToPersist.containsValue(Ref.of(e));
  }

  /**
   * Determine whether the given entity has a newer version. That means the given entity is an old entity.
   */
  boolean hasNewerVersion(Object e, MetaModel metaModel) {
    EntityKey key = new EntityKey(e.getClass(), metaModel.getIdOf(e));
    if (newVersionMapping.containsKey(key))
      return true;
    return versionsToPersist.containsKey(key);
  }

  /**
   * If the given entity has a newer version, return that version. Otherwise return <code>null
   * </code>.
   */
  Object newVersionOf(Object old, EntityManager em, MetaModel metaModel) {
    EntityKey oldKey = new EntityKey(old.getClass(), metaModel.getIdOf(old));
    Ref ref = versionsToPersist.get(oldKey);
    if (ref != null)
      return ref.referee;
    EntityKey newKey = newVersionMapping.get(oldKey);
    if (newKey != null)
      return em.getReference(newKey.getType(), newKey.getId());
    if (newVersionMapping.containsValue(oldKey))
      return old;
    return null;
  }
}
