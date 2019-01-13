package de.hbt.entity.versioning;

import static de.hbt.entity.versioning.ClassUtils.*;
import static de.hbt.entity.versioning.VersioningContext.*;

import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.Map.*;
import java.util.function.*;

import javax.persistence.*;
import javax.persistence.metamodel.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.*;

import de.hbt.entity.versioning.Association.*;
import de.hbt.entity.versioning.annotations.*;
import de.hbt.entity.versioning.exception.*;
import lombok.extern.slf4j.*;

/**
 * Hibernate {@link org.hibernate.Interceptor} overriding {@link #preFlush(Iterator)} in order to detect entity and
 * collection modifications to create new versions of entities and their associations for transparent versioning.
 *
 * <h2>How Does It Work?</h2>
 *
 * <p>
 * When Hibernate notifies the interceptor about entity modifications, there are two different types of modifications:
 *
 * <ul>
 * <li>scalar/simple properties (including JPA embeddables)
 * <li>collection updates (adding, removing, clearing of collection entries)
 * </ul>
 *
 * Either of those changes lead to the following:
 *
 * <ol>
 * <li>The entity Java instance on which the modifications were made is being deassociated from the persistence context
 * <li>Shallow copies of all collection associations of that entity are created and reassociated with that entity
 * <li>The old (current database version) of the modified entity is loaded from
 * <li>The old entity is marked as archived (later) the database (this entity will have all old persistent collections)
 * <li>All incoming associations where the old entity is the association end (i.e. the target) and for which version
 * cascades should be done are scanned and their owning entities loaded from the database
 * <li>For each such owning entity the collection is being modified to not include the old entity anymore but instead
 * the new entity
 * <li>The change of the collection owner leads to a new iteration of this list of steps from the top for that entity
 * </ol>
 *
 * <h2>How Does It Behave?</h2>
 *
 * <p>
 * The most important property of this solution is that flushing modifications made to any entities inside of a
 * transaction will modify the id property of those entity objects. This means that any entity instance may not be
 * stored in a hashCode()/equals()-sensitive datastructure over multiple flushes when hashCode/equals is defined over
 * the id of those entities.
 */
@Component
@SuppressWarnings("serial")
@Slf4j
class VersioningInterceptor extends org.hibernate.EmptyInterceptor {

  /**
   * We need to lazily lookup the {@link EntityManager} because it is being built when the {@link VersioningInterceptor}
   * is created.
   */
  @Autowired
  @Lazy
  private transient EntityManager entityManager;

  /**
   * The entity meta-model used to determine which entities need to potentially be updated when they reference another
   * entity for which a new version was created, and to find important annotated fields.
   */
  @Autowired
  private transient MetaModel metamodel;

  /** Supplier for "now". */
  @Autowired
  private transient Supplier<Instant> nowSupplier;

  /** Supplier for the current user. */
  @Autowired
  private transient Supplier<Principal> userSupplier;

  /**
   * Possible list of {@link VersioningListener} instances to be notified about certain versioning/lifecycle events.
   *
   * <p>
   * We need to inject them lazily because they might have a non-lazy dependency on the {@link EntityManager}
   * themselves, which will not be possible to resolve, because the EntityManager is being built AFTER the
   * {@link VersioningInterceptor} is created.
   */
  @Autowired(required = false)
  @Lazy
  private transient List<VersioningListener> versioningListeners;

  /**
   * {@link WeakHashMap} associating our {@link VersioningContext} with any currently active Hibernate transaction.
   */
  transient Map<org.hibernate.Transaction, VersioningContext> versioningContexts = new WeakHashMap<>();

  @Override
  public void afterTransactionCompletion(org.hibernate.Transaction tx) {
    versioningContexts.remove(tx);
  }

  /**
   * Called when a new entity is saved but BEFORE the {@link org.hibernate.action.internal.EntityInsertAction} is
   * created with the entity's state to be written to the database.
   */
  @Override
  public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames,
      org.hibernate.type.Type[] types) {
    Date createdAt = Date.from(nowSupplier.get());
    Class<?> realClass = findNonProxyClass(entity.getClass());
    if (createdAt == null) {
      throw new VersioningException("Creation date, as supplied by the Supplier<Instant>, when saving new ["
          + realClass.getSimpleName() + "] entity was null.", null);
    }
    String createdBy = userSupplier.get().getName();
    if (createdBy == null) {
      throw new VersioningException("Creating user, as supplied by the Supplier<Principal> when saving new ["
          + realClass.getSimpleName() + "] entity was null.", null);
    }
    metamodel.setModificationDate(entity, state, propertyNames, createdAt);
    metamodel.setModifyingUser(entity, state, propertyNames, createdBy);
    if (metamodel.isVersioned(entity)) {
      org.hibernate.engine.spi.SessionImplementor si = entityManager
          .unwrap(org.hibernate.engine.spi.SessionImplementor.class);
      org.hibernate.Transaction tx = si.getTransaction();
      VersioningContext vctx = versioningContextFor(tx);
      EntityKey key = new EntityKey(realClass, id);
      boolean isNewVersion = vctx.isNewVersion(entity, key);
      long version = metamodel.getVersionOf(entity);
      if (!isNewVersion && version != 0L) {
        throw new UnexpectedVersionException("Newly persisted entity [" + realClass.getSimpleName() + "#" + id
            + "] must have version = 0 but had version = " + version);
      } else if (!isNewVersion) {
        /* Set the initial version to 1 */
        metamodel.setVersionOf(entity, state, propertyNames, 1L);
        /* If there is a "(originally) creating user", set it now */
        metamodel.setCreatingUser(entity, state, propertyNames, createdBy);
        /* Set creation date */
        metamodel.setCreationDate(entity, state, propertyNames, createdAt);
        org.hibernate.FlushMode fm = si.getHibernateFlushMode();
        si.setHibernateFlushMode(org.hibernate.FlushMode.MANUAL);
        try {
          /* Check if the entity already has an identity, if not, create one */
          metamodel.ensureIdentity(entity, state, propertyNames, entityManager);
        } finally {
          si.setHibernateFlushMode(fm);
        }
        /* Mark this entity as its own newest version */
        vctx.newVersionMapping.put(key, key);
      }
    }
    return true;
  }

  /**
   * Get the {@link VersioningContext} for the given Hibernate Transaction.
   *
   * @param tx the Hibernate Transaction to get the {@link VersioningContext} for
   * @return the {@link VersioningContext}
   */
  VersioningContext versioningContextFor(org.hibernate.Transaction tx) {
    return versioningContexts.computeIfAbsent(tx, t -> new VersioningContext());
  }

  /**
   * preFlush is JUST THE RIGHT moment to apply modifications to the entities-to-be-flushed and their associated
   * collections and to create new entities, because it is here that any modified properties have not been fixed and no
   * actual update action has been created yet.
   *
   * <p>
   * All other events, such as {@link org.hibernate.event.spi.PreUpdateEventListener}, come too late and Hibernate will
   * already have added an update statement to the session's action queue.
   */
  @Override
  public void preFlush(@SuppressWarnings("rawtypes") Iterator it) {
    try {
      preFlushInternal(it);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new PersistenceException("Exception while handling versioning in Interceptor.preFlush", e);
    }
  }

  /**
   * Called from {@link #preFlush(Iterator)} to do the actual work and be able to throw exceptions.
   *
   * @param it the entities to be flushed
   * @throws Exception
   */
  private void preFlushInternal(Iterator<?> it) throws Exception {
    /* Cast that to our needed Hibernate interfaces */
    org.hibernate.engine.spi.SharedSessionContractImplementor ssci = entityManager
        .unwrap(org.hibernate.engine.spi.SharedSessionContractImplementor.class);
    org.hibernate.engine.spi.SessionImplementor si = entityManager
        .unwrap(org.hibernate.engine.spi.SessionImplementor.class);
    /* Create a shallow copy of the modified entities in the given Iterator 'it' */
    List<Object> entities = new ArrayList<>();
    while (it.hasNext())
      entities.add((Object) it.next());
    /* Get or create a VersioningContext for the current transaction */
    VersioningContext vctx = versioningContextFor(ssci.getTransaction());
    /*
     * Temporarily switch flush mode to manual.
     */
    org.hibernate.FlushMode fm = si.getHibernateFlushMode();
    si.setHibernateFlushMode(org.hibernate.FlushMode.MANUAL);
    try {
      /* Analyze the entities to find modified scalar properties */
      for (Object e : entities) {
        if (metamodel.isVersioned(e)) {
          /* Handle the modified entity */
          handle(e, entityManager, ssci, si, vctx);
        }
      }
      /*
       * Handle modified collections after handling the entities. This is necessary since entity modifications can lead
       * to collection elements being modified.
       */
      handleCollections(entityManager, si, ssci, vctx);
      /*
       * Mark old entity versions as archived, detach them from the persistence context as well as persist the new
       * versions.
       */
      afterUpdate(entityManager, vctx);
    } finally {
      /*
       * Reestablish the previous flush mode.
       */
      si.setHibernateFlushMode(fm);
    }
  }

  /**
   * Call {@link VersioningListener#onEntityArchived(Object, Serializable)} on all registered {@link VersioningListener
   * listeners} when the given entity <code>e</code> with the given <code>id</code> has been archived, because a new
   * version was created.
   *
   * @param e  the entity that has been archived
   * @param id the primary key
   */
  private void fireOnEntityArchived(Object e, Serializable id) {
    if (versioningListeners == null)
      return;
    for (VersioningListener listener : versioningListeners) {
      try {
        listener.onEntityArchived(e, id);
      } catch (Exception ex) {
        Class<?> realClass = findNonProxyClass(e.getClass());
        log.error("Exception while notifying [" + listener + "] about archived entity [" + realClass.getSimpleName()
            + "#" + id + "]", ex);
      }
    }
  }

  /**
   * Mark old entity versions as archived, detach them from the persistence context as well as persist the new versions.
   */
  private void afterUpdate(EntityManager em, VersioningContext vctx) {
    /*
     * For all old versions we did not yet update to set to archived, do it now.
     */
    for (Map.Entry<EntityKey, Object> e : vctx.versionsToArchive.entrySet()) {
      /*
       * Set old version to archived using a direct SQL UPDATE statement.
       */
      Serializable oldId = e.getKey().getId();
      Class<?> realClass = findNonProxyClass(e.getValue().getClass());
      String archivedField = metamodel.getArchivedFieldName(realClass);
      if (archivedField != null) {
        int count = em.createQuery("UPDATE " + findNonProxyClass(realClass).getSimpleName() + " SET " + archivedField
            + " = TRUE WHERE id = :id AND " + archivedField + " = FALSE").setParameter("id", oldId).executeUpdate();
        if (count != 1) { // sanity check
          throw new CouldNotArchiveException(
              "Could not set archived flag for an already archived [" + realClass.getSimpleName() + "#" + oldId + "]");
        }
        /* Notify listeners */
        fireOnEntityArchived(e.getValue(), oldId);
      }
      /*
       * ensure dirty and inconsistent object representing the old version is detached so that no collection/join tables
       * are updated/emptied.
       */
      em.detach(e.getValue());
    }
    vctx.versionsToArchive.clear();

    /*
     * Loop over each entity we created a new version of and persist it.
     *
     * Be VERY CAREFUL with how entries in the Map's entry set are removed. Because Map.entrySet() is backed by a Map
     * and since we are effectively modifying equals/hashCode of the values via EntityManager.persist(e), simply
     * iterating over the entry set and clearing the versionsToPersist afterwards will result in undefined behaviour!
     * For this reason: WE HAVE TO USE AN ITERATOR AND REMOVE ENTRIES FROM IT WHILE ITERATING!
     */
    Iterator<Entry<EntityKey, Ref>> iterator = vctx.versionsToPersist.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<EntityKey, Ref> entry = iterator.next();
      em.persist(entry.getValue().referee);
      iterator.remove();
      Class<?> realClass = findNonProxyClass(entry.getValue().referee.getClass());
      vctx.newVersionMapping.put(entry.getKey(), new EntityKey(realClass, metamodel.getIdOf(entry.getValue().referee)));
    }
  }

  /**
   * Check whether the given entity was updated, and if so, create a new version of that entity and of all other
   * entities that reference this entity via versioned associations.
   */
  private void handle(Object e, EntityManager em, org.hibernate.engine.spi.SharedSessionContractImplementor ssci,
      org.hibernate.engine.spi.SessionImplementor si, VersioningContext vctx) throws Exception {
    if (em.contains(e)) {
      handleScalarPropertiesModified(em, si, ssci, e, vctx, true, true);
    }
  }

  /**
   * Detect and handle all modifications on collections currently loaded in the persistence context.
   *
   * <p>
   * Other than scanning all collections for modifications/differences, we currently have no way of figuring out which
   * collections changed, because we HAVE TO process collection modifications in {@link #preFlush(Iterator)} and not in
   * {@link #onCollectionUpdate(Object, Serializable)}, since the latter comes too late in the process (i.e. we cannot
   * modify any entities anymore).
   */
  private void handleCollections(EntityManager em, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci, VersioningContext vctx) throws Exception {
    /*
     * First, figure out dirty collections. We HAVE TO do it here (and not in the veeery late called
     * Interceptor#onCollectionUpdate() to be able to modify the owning entities (change them to be archived).
     */
    @SuppressWarnings("unchecked")
    Map<org.hibernate.collection.spi.PersistentCollection, org.hibernate.engine.spi.CollectionEntry> collections = ssci
        .getPersistenceContext().getCollectionEntries();
    for (Map.Entry<org.hibernate.collection.spi.PersistentCollection, org.hibernate.engine.spi.CollectionEntry> e : collections
        .entrySet()) {
      org.hibernate.collection.spi.PersistentCollection pc = e.getKey();
      /*
       * Check if owner was not persisted yet or no versioned entity or collection was not modified
       */
      if (!metamodel.isVersioned(pc.getOwner()) || !isCollectionDirty(pc))
        continue;
      /*
       * Check if it was a composite association. In that case, we want to mark the deleted collection element as
       * deleted = true.
       */
      String roleName = collectionRole(pc, e.getValue());
      if (metamodel.isAssociationComposite(findNonProxyClass(pc.getOwner().getClass()), roleName)) {
        List<Object> deletedElements = deletedElements(em, pc, vctx);
        for (Object o : deletedElements) {
          metamodel.markAsDeleted(o);
          handleScalarPropertiesModified(em, si, ssci, o, vctx, false, false);
        }
      }
      handleCollectionModified(em, si, ssci, pc.getOwner(), pc, vctx);
    }
  }

  /**
   * Obtain the role (i.e. fully-qualified field name) of the given persistent collection.
   *
   * <p>
   * This is to workaround Hibernate bug https://hibernate.atlassian.net/browse/HHH-3129 because
   * PersistentCollection.getRole() can return <code>null</code>.
   *
   * @param pc the persistent collection
   * @param ce the collection entry
   * @return the role name
   */
  private static String collectionRole(org.hibernate.collection.spi.PersistentCollection pc,
      org.hibernate.engine.spi.CollectionEntry ce) {
    if (pc.getRole() != null)
      return pc.getRole();
    if (ce.getRole() != null)
      return ce.getRole();
    org.hibernate.persister.collection.CollectionPersister cp = ce.getCurrentPersister();
    if (cp == null)
      cp = ce.getLoadedPersister();
    if (cp != null) {
      if (cp.getRole() != null)
        return cp.getRole();
      org.hibernate.metadata.CollectionMetadata cm = cp.getCollectionMetadata();
      if (cm.getRole() != null)
        return cm.getRole();
    }
    throw new VersioningException("Cannot obtain role of persistent collection [" + pc + "]", null);
  }

  /**
   * Will be called whenever a new version of an entity was created. We either get notified about such an event via
   * {@link #preFlush(Iterator)} or via our own methods creating entities (e.g. when a collection owner was updated
   * because of an updated collection) inside of {@link #handleCollectionModified}.
   *
   * <p>
   * It is here that we detect and update any referencing entities through their associations.
   */
  private void handleNewVersionOfEntity(EntityManager em, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci, Object oldVersion, Object newVersion,
      VersioningContext vctx) throws Exception {
    /*
     * Get the type/class and the primary key of the old entity version. We will use this below to check for other
     * entities referencing the oldVersion.
     */
    Class<?> entityClass = findNonProxyClass(oldVersion.getClass());
    Serializable oldVersionKey = metamodel.getIdOf(oldVersion);
    /*
     * Check which potential other entities could reference the updated entity via some "version cascading" association
     * by using our association meta-model knowledge.
     */
    for (Association assoc : metamodel.getIncomingAssociations(entityClass)) {
      Class<?> referencingClass = assoc.getOwner();
      String associationName = assoc.getName();
      boolean isVersioned = metamodel.isVersioned(referencingClass);
      /*
       * Find all entities of that type which reference the updated entity via the given assocation (i.e. fieldName).
       * The association is either a scalar value or a collection, so use different queries for those two cases.
       */
      List<Object> owners;
      String archivedField = metamodel.getArchivedFieldName(referencingClass);
      if (assoc.getKind() == Kind.SINGULAR) {
        /*
         * Handle scalar/to-one associations. In this case, we simply use the fieldName.id
         */
        owners = si
            .createQuery("FROM " + referencingClass.getSimpleName() + " WHERE " + associationName + ".id = :id"
                + (isVersioned ? " AND " + archivedField + " = FALSE" : ""), Object.class)
            .setParameter("id", oldVersionKey).getResultList();
      } else {
        owners = si
            .createQuery("FROM " + referencingClass.getSimpleName() + " WHERE :entity MEMBER OF " + associationName
                + (isVersioned ? " AND " + archivedField + " = FALSE" : ""), Object.class)
            .setParameter("entity", oldVersion).getResultList();
      }
      /* For each such entity... */
      for (Object owner : owners) {
        handleNewVersionOfEntityForOwner(em, si, ssci, oldVersion, newVersion, vctx, assoc, isVersioned, owner);
      }
    }
  }

  private void handleNewVersionOfEntityForOwner(EntityManager em, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci, Object oldVersion, Object newVersion,
      VersioningContext vctx, Association assoc, boolean isVersioned, Object owner) throws Exception {
    /*
     * For fetch=LAZY associations we might get a proxy instance here, but we NEED to get to the real entity, so
     * unproxy() it. If the object is already the real entity, it is returned.
     */
    owner = si.getPersistenceContext().unproxy(owner);
    /* if it is a scalar/to-one reference... */
    if (assoc.getKind() == Kind.SINGULAR) {
      /*
       * Check if we already have a new version for this owner entity, in which case we need to set the new association
       * end to that new entity version.
       */
      Object newOwnerVersion = null;
      if (isVersioned) {
        newOwnerVersion = vctx.newVersionOf(owner, em, metamodel);
      }
      if (newOwnerVersion != null)
        owner = newOwnerVersion;
      /* Write the association to set the new version */
      assoc.write(owner, newVersion);
      /*
       * Has the referenced entity be marked as deleted and do we need to cascade that to the owner?
       */
      if (metamodel.isDeleted(newVersion) && assoc.isShouldCascadeDelete())
        metamodel.markAsDeleted(owner);
      if (isVersioned) {
        /*
         * Handle the to-one association modification as a scalar property change by calling
         * handleScalarPropertiesModified().
         *
         * NOTE: This has the nice benefit of handling other yet unprocessed scalar property changes on that entity as
         * well when creating a new version of the entity.
         */
        handleScalarPropertiesModified(em, si, ssci, owner, vctx, false, true);
      }
    } else {
      /* It is a collection, so obtain the collection from the referencing entity */
      Object modCollection = assoc.read(owner);
      /*
       * When we already handled this collection, we can abort here. This happens for the second time Hibernate wants to
       * initialize the collection of the same owner, since this PersistentCollection is still in the cache of the
       * CollectionLoader. Therefore, whenever we modify a PersistentCollection the first time (see code below this if
       * statement), we will get back the same PersistentCollection the second time.
       */
      if (vctx.handledCollections.containsKey(modCollection))
        return;
      /*
       * Explicitly mark the persistent collection as dirty.
       *
       * We need to do this when the owner is updated in a separate flush and afterwards a contained collection entry is
       * updated in another flush. In this case, the new owner won't contain the new version of the collection entry
       * anymore.
       */
      if (modCollection instanceof org.hibernate.collection.spi.PersistentCollection) {
        ((org.hibernate.collection.spi.PersistentCollection) modCollection).dirty();
      }
      /*
       * Update the collection to not contain the old entity version but instead the new entity version.
       *
       * NOTE: Use the primary key of the entity to remove and not any hashCode()/equals()-sensitive collection
       * operation, since the object hashCode/equals has changed due to the id change.
       */
      @SuppressWarnings("unchecked")
      Collection<Object> asCollection = (Collection<Object>) modCollection;
      Iterator<Object> iterator = asCollection.iterator();
      boolean containsNewVersion = false;
      while (iterator.hasNext()) {
        Object curr = iterator.next();
        if (curr == oldVersion)
          iterator.remove();
        containsNewVersion |= curr == newVersion;
      }
      if (!containsNewVersion)
        asCollection.add(newVersion);

      /* Continue handling a collection change */
      if (isVersioned) {
        handleCollectionModified(em, si, ssci, owner, (org.hibernate.collection.spi.PersistentCollection) modCollection,
            vctx);
      }
    }
  }

  /**
   * Process any changed scalar properties of the given entity 'e'.
   *
   * <p>
   * This method compares the database state of the entity with the current entity state in the persistence context, and
   * if they differ, create a new version of that entity. It then delegates to {@link #handleNewVersionOfEntity} to
   * update any affected entities as well.
   *
   * @param em              the {@link EntityManager}
   * @param si              the {@link org.hibernate.engine.spi.SessionImplementor} view on that {@link EntityManager}
   * @param ssci            the {@link org.hibernate.engine.spi.SharedSessionContractImplementor} view on that
   *                        {@link EntityManager}
   * @param e               the entity to process, must be attached to the given {@link EntityManager}
   * @param vctx            the versioning context
   * @param checkDirty      whether to check if there are dirty properties
   * @param propagateChange whether the creation of a new entity version should be propagated to associated entities
   */
  private void handleScalarPropertiesModified(EntityManager em, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci, Object e, VersioningContext vctx,
      boolean checkDirty, boolean propagateChange) throws Exception {
    /*
     * Is the modified entity already a new version? In that case we don't need to do anything anymore, since all
     * modifications already happened on the new version.
     */
    if (vctx.isNewVersion(e, metamodel))
      return;
    Class<?> realClass = findNonProxyClass(e.getClass());
    org.hibernate.persister.entity.EntityPersister ep = ssci.getFactory().getMetamodel().entityPersister(realClass);
    Serializable eKey = metamodel.getIdOf(e);
    /*
     * Do we have dirty and updatable scalar properties? Note that an entity will usually define the audit metadata
     * columns as updatable=false, so that when merging an entity's state into the database, only the non-metadata state
     * can be updated and only those columns will be used for the dirty check.
     */
    if (checkDirty && !hasDirtyUpdatableScalarProperties(e, eKey, ep, si, ssci)) {
      return;
    }
    /*
     * Check if we are working on an already archived entity.
     */
    if (metamodel.isArchived(e)) {
      throw new ModifiedArchivedException(
          "Modified already archived entity [" + realClass.getSimpleName() + "#" + eKey + "]");
    }
    /*
     * Create a new version for that entity.
     */
    Object oldVersion = update(e, em, ssci, si, ep, vctx);
    Object newVersion = e;
    if (oldVersion != e && propagateChange)
      handleNewVersionOfEntity(em, si, ssci, oldVersion, newVersion, vctx);
  }

  /**
   * Check whether there are any differences of scalar and updatable property values between the current state of the
   * given JPA entity and the current database snapshot.
   *
   * @param e    the JPA entity object
   * @param eKey the identifier/primary key of the entity
   * @param ep   the Hibernate EntityPersister corresponding to the given JPA entity
   * @param ssci the {@link org.hibernate.engine.spi.SharedSessionContractImplementor} view on that
   *             {@link EntityManager}
   * @return <code>true</code> if there is a difference; <code>false</code> otherwise
   */
  boolean hasDirtyUpdatableScalarProperties(Object e, Serializable eKey,
      org.hibernate.persister.entity.EntityPersister ep, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci) {
    /*
     * Retrieve a fresh copy of the current database snapshot. This needs a bit more explanation: Hibernate will execute
     * a SELECT statement to grab the column values of the entity from the database. Since Hibernate will ONLY fetch the
     * actual columns of the entity's database table, collections WILL NOT BE contained in the snapshot array. Also,
     * non-updatable columns will have null values in the returned array!
     */
    Object[] databaseSnapshot = ep.getDatabaseSnapshot(eKey, ssci);
    /* Was this entity already persisted in the database? */
    if (databaseSnapshot == null)
      return false;
    /* Retrieve the current values of the JPA entity POJO */
    Object[] propertyValues = ep.getPropertyValues(e);
    /* Ask which properties are updatable */
    boolean[] updatable = ep.getPropertyUpdateability();
    org.hibernate.type.Type[] propertyTypes = ep.getPropertyTypes();
    String[] propertyNames = ep.getPropertyNames();
    return hasDirtyUpdatableScalarProperties(e, si, ssci, databaseSnapshot, propertyValues, propertyNames, updatable,
        propertyTypes);
  }

  /**
   * Given the array of database snapshot property values and JPA entity property values, check whether there is a
   * difference between the two.
   *
   * <p>
   * This method only checks for changes in scalar and updatable properties.
   *
   * <p>
   * In addition to checking for differences in the properties, this method will also reset any possible modified
   * {@link Identity} property to the database snapshot value. This is to prevent Hibernate from persisting any possibly
   * changed/new identity object.
   *
   * @param e                the JPA entity whose properties are checked
   * @param si               the {@link org.hibernate.engine.spi.SessionImplementor} view on that {@link EntityManager}
   * @param ssci             the {@link org.hibernate.engine.spi.SharedSessionContractImplementor} view on that
   *                         {@link EntityManager}
   * @param databaseSnapshot the database snapshot values
   * @param propertyValues   the JPA entity's property values
   * @param propertyNames    the names of the properties
   * @param updatable        information about which properties are updatable
   * @param propertyTypes    the Hibernate types of the properties
   * @return <code>true</code> if there is a difference; <code>false</code> otherwise
   */
  private boolean hasDirtyUpdatableScalarProperties(Object e, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci, Object[] databaseSnapshot,
      Object[] propertyValues, String[] propertyNames, boolean[] updatable, org.hibernate.type.Type[] propertyTypes) {
    /*
     * Iterate over each property and check whether the database snapshot version differs from the JPA entity property.
     */
    for (int i = 0; i < databaseSnapshot.length; i++) {
      /*
       * Only check scalar and updatable properties.
       */
      if (!isScalarProperty(i, propertyTypes) || (updatable != null && !updatable[i]))
        continue;
      Object dbValue = databaseSnapshot[i];
      Object propVal = propertyValues[i];
      /*
       * First, check whether this is an @Identity field which should be updatable.
       */
      if (propertyNames != null && metamodel.isIdentityProperty(e, propertyNames[i])) {
        /* Reset identity */
        Class<?> identityType = (Class<?>) propertyTypes[i].getReturnedClass();
        if (identityType != Long.class) {
          dbValue = si.getReference(identityType, dbValue);
        }
        metamodel.setIdentity(e, dbValue);
        /* Proceed with next property. */
        continue;
      }
      if ((dbValue == null) != (propVal == null)) {
        /*
         * One is null where the other is not.
         */
        return handlePropertyModified(e, propertyNames, i);
      } else if (propVal == null) {
        /*
         * They are both null. Follows from condition above.
         */
        continue;
      } else if (propertyTypes[i].isEntityType()) {
        /*
         * Snapshot will have the id and property value will be the JPA entity. So, extract the id from the JPA entity
         * object.
         */
        propVal = metamodel.getIdOf(propVal);
        if (propVal == null) {
          return handlePropertyModified(e, propertyNames, i);
        }
      } else if (propertyTypes[i].isComponentType()) {
        /* This is a component/embeddable. Compare sub-properties */
        org.hibernate.type.ComponentType ct = (org.hibernate.type.ComponentType) propertyTypes[i];
        if (hasDirtyUpdatableScalarProperties(propVal, dbValue, ct, si, ssci)) {
          /* Components differ. */
          return true;
        } else {
          /* Components do not differ. Proceed with next property. */
          continue;
        }
      }
      if (dbValue instanceof BigDecimal) {
        /*
         * Special handling for comparing BigDecimal.
         */
        if (((BigDecimal) dbValue).compareTo((BigDecimal) propVal) != 0)
          return handlePropertyModified(e, propertyNames, i);
      } else if (!propVal.equals(dbValue)) {
        /*
         * Everything else will be compared with equals().
         */
        return handlePropertyModified(e, propertyNames, i);
      }
    }
    return false;
  }

  /**
   * Will be called by
   * {@link #hasDirtyUpdatableScalarProperties(Object, org.hibernate.engine.spi.SessionImplementor, org.hibernate.engine.spi.SharedSessionContractImplementor, Object[], Object[], String[], boolean[], org.hibernate.type.Type[])}
   * to check whether updating that property is allowed.
   *
   * @param e             the entity whose property was modified
   * @param propertyNames the names of the properties
   * @param propertyIndex the index of the modified property in the <code>propertyValues</code>,
   *                      <code>propertyNames</code> and <code>propertyTypes</code> array
   * @return <code>true</code>
   */
  private boolean handlePropertyModified(Object e, String[] propertyNames, int propertyIndex) {
    if (propertyNames != null && metamodel.isArchivedProperty(e, propertyNames[propertyIndex])) {
      /* Manually changing the archived property is not allowed. */
      throw new IllegalPropertyModificationException(
          "Cannot modify property [" + propertyNames[propertyIndex] + "] on automatically versioned entity ["
              + findNonProxyClass(e.getClass()) + "#" + metamodel.getIdOf(e) + "]");
    }
    return true;
  }

  /**
   * Check whether any sub-property of a component/embeddable is different.
   *
   * @param propVal the JPA entity's property value. This will be an instance of a class annotated with
   *                {@link Embeddable}
   * @param dbValue the database snapshot value. This will be an array with the sub-property values
   * @param ct      the Hibernate ComponentType of the embeddable
   * @param si      the {@link org.hibernate.engine.spi.SessionImplementor} view on that {@link EntityManager}
   * @param ssci    the {@link org.hibernate.engine.spi.SharedSessionContractImplementor} view on that
   *                {@link EntityManager}
   * @return <code>true</code> if there is a difference; <code>false</code> otherwise
   */
  private boolean hasDirtyUpdatableScalarProperties(Object propVal, Object dbValue, org.hibernate.type.ComponentType ct,
      org.hibernate.engine.spi.SessionImplementor si, org.hibernate.engine.spi.SharedSessionContractImplementor ssci) {
    Object[] dbValues = (Object[]) dbValue;
    Object[] propVals = ct.getPropertyValues(propVal, ssci);
    return hasDirtyUpdatableScalarProperties(propVal, si, ssci, dbValues, propVals, null, null, ct.getSubtypes());
  }

  /**
   * Copy all collection attributes of the given entity. This avoids errors due to collections having different loaded
   * and current keys when we create a new version of the owning entity.
   *
   * @param e  the entity whose collections should be copied
   * @param ep the {@link org.hibernate.persister.entity.EntityPersister} of the entity whose collections to copy
   */
  private static void shallowCopyAllCollectionsOf(Object e, org.hibernate.persister.entity.EntityPersister ep) {
    Class<?> realClass = findNonProxyClass(e.getClass());
    Set<?> pluralAttributes = ep.getFactory().getMetamodel().entity(realClass).getPluralAttributes();
    for (Object attribute : pluralAttributes) {
      PluralAttribute<?, ?, ?> pa = (PluralAttribute<?, ?, ?>) attribute;
      @SuppressWarnings("unchecked")
      Collection<Object> coll = (Collection<Object>) ep.getPropertyValue(e, pa.getName());
      Class<?> realCollectionClass = findNonProxyClass(coll.getClass());
      Collection<Object> newColl = instantiateCollectionLike(realCollectionClass);
      newColl.addAll(coll);
      ep.setPropertyValue(e, findPropertyIndex(ep, pa.getName()), newColl);
    }
  }

  /**
   * Create a new version of the entity by removing it from the persistence context, copying all collections (including
   * the modified collection!).
   *
   * <p>
   * Additionally, the id of the entity is nulled so as to persist it later as a new entity and the version is
   * incremented.
   */
  private Object update(Object e, EntityManager em, org.hibernate.engine.spi.SharedSessionContractImplementor ssci,
      org.hibernate.engine.spi.SessionImplementor si, org.hibernate.persister.entity.EntityPersister ep,
      VersioningContext vctx) {
    /*
     * Remove the entity 'e', which will soon become our new version, from the persistence context. This will ensure
     * that a later persist() of that entity will obtain a new identifier and create a new entity in the database. And
     * that new entity will then have all our Java property modifications applied to it.
     */
    org.hibernate.engine.spi.EntityKey entityKey = si.getPersistenceContext().getEntry(e).getEntityKey();
    si.getPersistenceContext().removeEntry(e);
    si.getPersistenceContext().removeEntity(entityKey);
    /*
     * 'e' will now become the new version.
     */
    Object newVersion = e;
    /*
     * Reload a fresh copy of the old (currently persisted) database version of that entity.
     */
    Serializable oldId = ep.getIdentifier(newVersion, ssci);
    Class<?> realClass = findNonProxyClass(newVersion.getClass());
    Object oldVersion = em.find(realClass, oldId);
    /*
     * Check if the modified entity has the expected version.
     */
    long oldVersionNumber = metamodel.getVersionOf(oldVersion);
    long newVersionNumber = metamodel.getVersionOf(newVersion);
    if (oldVersionNumber != newVersionNumber) {
      throw new ModifiedUnexpectedVersionException("Modified unexpected version [" + newVersionNumber + "] of entity ["
          + realClass.getSimpleName() + "#" + oldId + "] having different version [" + oldVersionNumber + "]");
    }
    /*
     * Build the entity key for that entity.
     */
    EntityKey oldVersionKey = new EntityKey(realClass, oldId);
    /*
     * Create a shallow copy of all collection attributes of e/newVersion. Why do we do this? => If we did not do this,
     * then the collections would have different loaded and current keys of the owning entity, since we are removing the
     * entity in order to create a new version of it.
     */
    shallowCopyAllCollectionsOf(newVersion, ep);
    /*
     * Null-out the identifier of the entity in order for a future persist() call to create a new entity from it.
     */
    ep.setIdentifier(newVersion, null, ssci);
    /*
     * And increment the version.
     */
    metamodel.incrementVersion(oldVersion, newVersion);
    /*
     * Make sure that any identity field remains const.
     */
    metamodel.copyIdentity(oldVersion, newVersion);
    /*
     * Make sure that the creating user (the original creator) remains the same.
     */
    metamodel.copyCreatingUser(oldVersion, newVersion);
    /*
     * Make sure that the creation date (the original date) remains the same.
     */
    metamodel.copyCreationDate(oldVersion, newVersion);
    /*
     * Associate old -> new in order to avoid creating yet newer versions of that entity when processing other entity
     * and collection changes which might cascade to this entity.
     *
     * First, remember that we need to persist a new version. This is keyed by the old version
     */
    vctx.versionsToPersist.put(oldVersionKey, Ref.of(newVersion));
    /*
     * Next, remember to archive the old version.
     */
    vctx.versionsToArchive.put(oldVersionKey, oldVersion);
    /*
     * Return the oldVersion freshly loaded from the database.
     */
    return oldVersion;
  }

  /**
   * Determine whether the property at the given index has a scalar type, making it eligible for dirty-checking inside
   * of {@link #handleScalarPropertiesModified}.
   *
   * @param index the index of the property
   * @param types all property types (by index)
   * @return <code>true</code> if the property is scalar or <code>false</code>
   */
  private static boolean isScalarProperty(int index, org.hibernate.type.Type[] types) {
    org.hibernate.type.Type t = types[index];
    return t.isEntityType() || !t.isEntityType() && !t.isCollectionType();
  }

  /**
   * Check whether the given {@link org.hibernate.collection.spi.PersistentCollection} is dirty. If the collection has
   * not yet been stored in the database (i.e. its stored snapshot is <code>
   * null</code>) this method returns <code>false</code>.
   *
   * @param pc the {@link org.hibernate.collection.spi.PersistentCollection}
   */
  private static boolean isCollectionDirty(org.hibernate.collection.spi.PersistentCollection pc) {
    Collection<?> current = (Collection<?>) pc;
    Collection<?> stored = (Collection<?>) pc.getStoredSnapshot();
    if (stored == null)
      return false;
    return !current.containsAll(stored) || !stored.containsAll(current);
  }

  /**
   * Obtain all entities that were deleted from the given {@link org.hibernate.collection.spi.PersistentCollection}.
   *
   * @param em   the {@link EntityManager}
   * @param pc   the {@link org.hibernate.collection.spi.PersistentCollection}
   * @param vctx the {@link VersioningContext} of the current transaction
   * @return the list of all effectively deleted entities
   */
  private List<Object> deletedElements(EntityManager em, org.hibernate.collection.spi.PersistentCollection pc,
      VersioningContext vctx) {
    Collection<?> current = (Collection<?>) pc;
    Collection<?> stored = (Collection<?>) pc.getStoredSnapshot();
    if (stored == null)
      return Collections.emptyList();
    List<Object> deleted = new ArrayList<>();
    /* Iterate over all entities in the database snapshot */
    for (Object o : stored) {
      /* obtain the possibly new version of that entity */
      Object newestVersion = vctx.newVersionOf(o, em, metamodel);
      if (newestVersion != null) {
        o = newestVersion;
      }
      /*
       * and check whether the current/new state of the collection does not contain that entity.
       */
      if (!current.contains(o)) {
        deleted.add(o);
      }
    }
    return deleted;
  }

  /**
   * Instantiate a new {@link Collection} based on a common interface type, such as {@link List} or {@link Set}
   * implemented by the given collection class.
   *
   * @param clazz the collection class based on which to create a new instance
   * @return the newly created empty collection
   */
  private static Collection<Object> instantiateCollectionLike(Class<?> clazz) {
    if (List.class.isAssignableFrom(clazz))
      return new ArrayList<>();
    else if (Set.class.isAssignableFrom(clazz))
      return new HashSet<>();
    throw new CollectionInstantiationException("Could not instantiate new collection of type [" + clazz + "]");
  }

  /**
   * A collection was already modified in an original entity, either via the user or via modifying a dependent versioned
   * entity inside of this {@link VersioningInterceptor}.
   */
  private void handleCollectionModified(EntityManager em, org.hibernate.engine.spi.SessionImplementor si,
      org.hibernate.engine.spi.SharedSessionContractImplementor ssci, Object owner,
      org.hibernate.collection.spi.PersistentCollection pc, VersioningContext vctx) throws Exception {
    /*
     * Check if we already processed this persistent collection (by object identity). This happens when a scalar
     * property of entity A was modified as well as an entity B contained in a collection owned by A. When we get
     * notified about the scalar value changes of B (inside preFlush()) and process those changes, we create a new
     * collection in A. Afterwards, we process the collections inside the persistence context, one of which was the
     * modified collection, which we already processed before during the scalar property modification. In this case, we
     * must skip processing the collection again since that would otherwise result in yet a new version of the owner
     * being created.
     */
    if (vctx.handledCollections.containsKey(pc)) {
      return;
    }
    vctx.handledCollections.put(pc, pc);
    Class<?> realClass = findNonProxyClass(owner.getClass());
    org.hibernate.persister.entity.EntityPersister ep = si.getFactory().getMetamodel().entityPersister(realClass);
    Serializable ownerId = metamodel.getIdOf(owner);
    /*
     * If we are already working on a new version of an entity. Do nothing!
     */
    if (vctx.isNewVersion(owner, new EntityKey(realClass, ownerId))) {
      return;
    }
    /*
     * Do we already have a new version for that entity? If so, we don't have to do anything anymore, since in that
     * case, we already modified the collection by other means (i.e. via a new version of an entity contained within a
     * collection).
     */
    if (vctx.hasNewerVersion(owner, metamodel))
      return;
    /*
     * Check if we are working on an already archived entity.
     */
    if (metamodel.isArchived(owner)) {
      throw new ModifiedArchivedException(
          "Modified already archived entity [" + realClass.getSimpleName() + "#" + ownerId + "]");
    }
    /*
     * Create a new version of the entity by removing it from the persistence context and copying all collections
     * (including the modified collection!).
     */
    Object oldVersion = update(owner, em, ssci, si, ep, vctx);
    /*
     * Handle that a new version of that entity was created. This may create new versions of entities referencing this
     * modified entity via some "version cascading" association(s).
     */
    handleNewVersionOfEntity(em, si, ssci, oldVersion, owner, vctx);
  }
}
