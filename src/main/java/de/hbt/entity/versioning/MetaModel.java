package de.hbt.entity.versioning;

import static de.hbt.entity.versioning.ClassUtils.*;
import static de.hbt.entity.versioning.VersioningContext.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;

import javax.persistence.*;
import javax.persistence.metamodel.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.*;

import de.hbt.entity.versioning.Association.*;
import de.hbt.entity.versioning.annotations.*;
import de.hbt.entity.versioning.annotations.Version;
import de.hbt.entity.versioning.exception.*;
import lombok.*;

/**
 * Computes and holds information about the entity metamodel used for the versioning system.
 *
 * <p>
 * <em>This class is only to be used by the classes inside the {@link de.hbt.entity.versioning} package and
 * sub-packages!</em>
 */
@Component
@Data
class MetaModel {

  static class ClassMetaModel {
    /**
     * All incoming associations to this class (i.e. where this class is the end of the directed association).
     */
    List<Association> incomingAssociations = new ArrayList<>();
    /**
     * All outgoing associations from this class (i.e. where this class is the start of the directed association).
     */
    Map<String, Association> outgoingAssociations = new HashMap<>();
    /** The field that holds the initially creating user. */
    String creatingUserField;

    Method creatingUserSetter;
    Method creatingUserGetter;
    /** The field that holds the updating user. */
    String modifyingUserField;

    Method modifyingUserSetter;
    Method modifyingUserGetter;
    /** The field that holds the date/time of the initial version. */
    String creationDateField;

    Method creationDateSetter;
    Method creationDateGetter;
    /** The field that holds the modification date. */
    String modificationDateField;

    Method modificationDateSetter;
    Method modificationDateGetter;
    /** The field that holds whether the entity was softly deleted. */
    String softDeletedField;

    Method softDeletedSetter;
    Method softDeletedGetter;
    /**
     * The field that holds whether the entity is archived (i.e. whether a newer version of that entity exists).
     */
    String archivedField;

    Method archivedSetter;
    Method archivedGetter;
    /** The field/setter/getter of the identity of a versioned entity. */
    String identityField;

    Method identitySetter;
    Method identityGetter;
    Identity identityAnnotation;
    /** The field/setter/getter of the id of an entity. */
    String idField;

    Method idSetter;
    Method idGetter;
    /** The field/setter/getter of the version of a versioned entity. */
    String versionField;

    Method versionSetter;
    Method versionGetter;
    /** Whether the entity type is versioned. */
    boolean versioned;
  }

  /**
   * Keeps the meta-models of all associations indexed by referenced class (the end of the directed association).
   */
  private Map<Class<?>, ClassMetaModel> classMetaModels;

  /**
   * Inject the EntityManager lazily, because it is being built BEFORE this {@link MetaModel} class, which is a
   * dependency of {@link VersioningInterceptor}.
   */
  @Autowired
  @Lazy
  private EntityManager entityManager;

  private ClassMetaModel classMetaModelOf(Class<?> clazz) {
    if (classMetaModels == null) {
      try {
        buildMetaModel();
      } catch (Exception e) {
        throw new AssertionError("Could not build JPA meta model", e);
      }
    }
    return classMetaModels.get(clazz);
  }

  /**
   * Get the associations for which the given class is the referenced entity.
   *
   * @param clazz the referenced entity type
   * @return the "incoming" associations for the given class
   */
  public List<Association> getIncomingAssociations(Class<?> clazz) {
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return Collections.emptyList();
    List<Association> associations = classMetaModel.incomingAssociations;
    if (associations == null)
      return Collections.emptyList();
    return associations;
  }

  /**
   * Find the outgoing association with the given name in the given class.
   *
   * @param clazz the referencing entity type
   * @param name  the association name (i.e. the field name)
   * @return the "outgoing" association or <code>null</code>
   */
  public Association findOutgoingAssociation(Class<?> clazz, String name) {
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return null;
    return classMetaModel.outgoingAssociations.get(name);
  }

  private static <T extends Annotation> T memberAnnotation(Member member, Class<T> clazz) {
    if (member instanceof AccessibleObject) {
      AccessibleObject ao = (AccessibleObject) member;
      return ao.getAnnotation(clazz);
    }
    return null;
  }

  /**
   * We have to do a little bit of Reflection to obtain the setter from a given getter Member, which can either be a
   * Field or a Method, depending on where the JPA Id annotation was placed in the entity class.
   *
   * <p>
   * Since the JPA metamodel only knows the "javaMember" of an association, and this is either the Field or the
   * <em>getter</em> Method, we have to figure out the setter.
   */
  private static Member determineSetter(Member getter, String name) {
    if (getter instanceof Field) {
      /*
       * Simply use the Field.
       */
      return getter;
    } else if (getter instanceof Method) {
      /*
       * Check whether there is a "set" method with an expected name.
       */
      Method method = (Method) getter;
      String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
      try {
        return getter.getDeclaringClass().getMethod(setterName, method.getReturnType());
      } catch (NoSuchMethodException | SecurityException e) {
        throw new InternalVersioningException("Could not access setter", e);
      }
    } else {
      throw new IllegalArgumentException("Unsupported Java getter member [" + getter + "]");
    }
  }

  /**
   * Build a meta-model of the entities with their important associations and fields for all JPA entities.
   *
   * @param entityManager the {@link EntityManager}
   */
  public void buildMetaModel() {
    classMetaModels = new IdentityHashMap<>();
    Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
    for (EntityType<?> entity : entities) {
      Class<?> clazz = findNonProxyClass(entity.getJavaType());
      ClassMetaModel classMetaModel = classMetaModels.computeIfAbsent(clazz, c -> new ClassMetaModel());
      /* Identify associations using JPA Attributes on the EntityType */
      for (Attribute<?, ?> a : entity.getAttributes()) {
        if (a.isAssociation()) {
          /* It is an entity association (either to-one or to-many) */
          handleAssociation(clazz, classMetaModel, a);
        } else if (!a.isCollection()) {
          /* It is a primitive attribute */
          handlePrimitive(clazz, classMetaModel);
        }
      }
    }
  }

  private void handlePrimitive(Class<?> clazz, ClassMetaModel classMetaModel) {
    Class<?> currentClass = clazz;
    while (currentClass != null) {
      Field[] fields = currentClass.getDeclaredFields();
      for (Field f : fields) {
        if (f.isAnnotationPresent(CreatingUser.class)) {
          classMetaModel.creatingUserField = f.getName();
          classMetaModel.creatingUserSetter = findSetter(f);
          classMetaModel.creatingUserGetter = findGetter(f);
        } else if (f.isAnnotationPresent(ModifyingUser.class)) {
          classMetaModel.modifyingUserField = f.getName();
          classMetaModel.modifyingUserSetter = findSetter(f);
          classMetaModel.modifyingUserGetter = findGetter(f);
        } else if (f.isAnnotationPresent(Id.class)) {
          classMetaModel.idField = f.getName();
          classMetaModel.idSetter = findSetter(f);
          classMetaModel.idGetter = findGetter(f);
        } else if (f.isAnnotationPresent(CreationDate.class)) {
          classMetaModel.creationDateField = f.getName();
          classMetaModel.creationDateSetter = findSetter(f);
          classMetaModel.creationDateGetter = findGetter(f);
        } else if (f.isAnnotationPresent(ModificationDate.class)) {
          classMetaModel.modificationDateField = f.getName();
          classMetaModel.modificationDateSetter = findSetter(f);
          classMetaModel.modificationDateGetter = findGetter(f);
        } else if (f.isAnnotationPresent(SoftDeleted.class)) {
          classMetaModel.softDeletedField = f.getName();
          classMetaModel.softDeletedSetter = findSetter(f);
          classMetaModel.softDeletedGetter = findGetter(f);
        } else if (f.isAnnotationPresent(Archived.class)) {
          classMetaModel.archivedField = f.getName();
          classMetaModel.archivedSetter = findSetter(f);
          classMetaModel.archivedGetter = findGetter(f);
        } else if (f.isAnnotationPresent(Version.class)) {
          classMetaModel.versionField = f.getName();
          classMetaModel.versionSetter = findSetter(f);
          classMetaModel.versionGetter = findGetter(f);
          classMetaModel.versioned = true;
        } else if (f.isAnnotationPresent(Identity.class)) {
          classMetaModel.identityField = f.getName();
          classMetaModel.identitySetter = findSetter(f);
          classMetaModel.identityGetter = findGetter(f);
          classMetaModel.identityAnnotation = f.getAnnotation(Identity.class);
        }
      }
      currentClass = currentClass.getSuperclass();
    }
  }

  private static boolean hasPersistOrMerge(ManyToOne annot) {
    if (annot == null)
      return false;
    for (CascadeType cascadeType : annot.cascade())
      if (cascadeType == CascadeType.ALL || cascadeType == CascadeType.PERSIST || cascadeType == CascadeType.MERGE)
        return true;
    return false;
  }

  private static boolean hasPersistOrMerge(ManyToMany annot) {
    if (annot == null)
      return false;
    for (CascadeType cascadeType : annot.cascade())
      if (cascadeType == CascadeType.ALL || cascadeType == CascadeType.PERSIST || cascadeType == CascadeType.MERGE)
        return true;
    return false;
  }

  private void handleAssociation(Class<?> clazz, ClassMetaModel classMetaModel, Attribute<?, ?> a) {
    Kind kind = null;
    Class<?> returnType;
    Member member = a.getJavaMember();
    Member setter = determineSetter(member, a.getName());
    if (a.isCollection()) {
      kind = Kind.PLURAL;
      PluralAttribute<?, ?, ?> pa = (PluralAttribute<?, ?, ?>) a;
      javax.persistence.metamodel.Type<?> elementType = pa.getElementType();
      returnType = elementType.getJavaType();
    } else {
      kind = Kind.SINGULAR;
      SingularAttribute<?, ?> sa = (SingularAttribute<?, ?>) a;
      returnType = sa.getJavaType();
    }
    /*
     * Check whether a version update should cascade to owning entities.
     */
    boolean shouldCascadeVersion = false;
    boolean shouldCascadeDelete = false;
    CascadeNewVersion cascadeNewVersionOnClass = clazz.getAnnotation(CascadeNewVersion.class);
    CascadeNewVersion cascadeNewVersionOnPackage = clazz.getPackage().getAnnotation(CascadeNewVersion.class);
    CascadeNewVersion cascadeNewVersionAnnot = memberAnnotation(member, CascadeNewVersion.class);
    ManyToOne manyToOne = memberAnnotation(member, ManyToOne.class);
    ManyToMany manyToMany = memberAnnotation(member, ManyToMany.class);
    if (cascadeNewVersionAnnot != null) {
      shouldCascadeVersion = cascadeNewVersionAnnot.value();
      shouldCascadeDelete = cascadeNewVersionAnnot.withDelete();
    } else if (hasPersistOrMerge(manyToOne)) {
      shouldCascadeVersion = true;
    } else if (hasPersistOrMerge(manyToMany)) {
      shouldCascadeVersion = true;
    } else if (cascadeNewVersionOnClass != null) {
      shouldCascadeVersion = cascadeNewVersionOnClass.value();
      shouldCascadeDelete = cascadeNewVersionOnClass.withDelete();
    } else if (cascadeNewVersionOnPackage != null) {
      shouldCascadeVersion = cascadeNewVersionOnPackage.value();
      shouldCascadeDelete = cascadeNewVersionOnPackage.withDelete();
    }
    /* Find @Identity member annotation */
    Identity identity = memberAnnotation(member, Identity.class);
    if (identity != null) {
      classMetaModel.identityField = member.getName();
      classMetaModel.identitySetter = findSetter(member);
      classMetaModel.identityGetter = findGetter(member);
      classMetaModel.identityAnnotation = identity;
    }
    if (shouldCascadeVersion) {
      /*
       * Add the association indexed by the class of the referenced type.
       */
      ClassMetaModel referencedClassMetaModel = classMetaModels.get(returnType);
      if (referencedClassMetaModel == null) {
        referencedClassMetaModel = new ClassMetaModel();
        classMetaModels.put(returnType, referencedClassMetaModel);
      }
      Association association = new Association();
      association.setKind(kind);
      association.setName(a.getName());
      association.setOwner(clazz);
      association.setGetter(findGetter(member));
      association.setSetter(findSetter(setter));
      association.setShouldCascadeNewVersion(shouldCascadeVersion);
      association.setShouldCascadeDelete(shouldCascadeDelete);
      referencedClassMetaModel.incomingAssociations.add(association);
      classMetaModel.outgoingAssociations.put(a.getName(), association);
    }
  }

  public Serializable getIdOf(Object entity) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    try {
      return (Serializable) classMetaModel.idGetter.invoke(entity);
    } catch (Exception e) {
      throw new InternalVersioningException(
          "Cannot obtain id of entity [" + entity + "] stored in [" + classMetaModel.idField + "]", e);
    }
  }

  public void setModificationDate(Object entity, Object[] state, String[] propertyNames, Date date) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.modificationDateField == null)
      return;
    try {
      classMetaModel.modificationDateSetter.invoke(entity, date);
      state[findPropertyIndex(clazz, propertyNames, classMetaModel.modificationDateField)] = date;
    } catch (Exception e) {
      throw new AuditingException("Could not set modification date on entity [" + entity + "]", e);
    }
  }

  public void setModifyingUser(Object entity, Object[] state, String[] propertyNames, String user) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.modifyingUserField == null)
      return;
    try {
      classMetaModel.modifyingUserSetter.invoke(entity, user);
      state[findPropertyIndex(clazz, propertyNames, classMetaModel.modifyingUserField)] = user;
    } catch (Exception e) {
      throw new AuditingException("Could not set modifying user on entity [" + entity + "]", e);
    }
  }

  public void setCreationDate(Object entity, Object[] state, String[] propertyNames, Date date) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.creationDateField == null)
      return;
    try {
      classMetaModel.creationDateSetter.invoke(entity, date);
      state[findPropertyIndex(clazz, propertyNames, classMetaModel.creationDateField)] = date;
    } catch (Exception e) {
      throw new AuditingException("Could not set creation date on entity [" + entity + "]", e);
    }
  }

  public void setCreatingUser(Object entity, Object[] state, String[] propertyNames, String user) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.creatingUserField == null)
      return;
    try {
      classMetaModel.creatingUserSetter.invoke(entity, user);
      state[findPropertyIndex(clazz, propertyNames, classMetaModel.creatingUserField)] = user;
    } catch (Exception e) {
      throw new AuditingException("Could not set creating user on entity [" + entity + "]", e);
    }
  }

  public void markAsDeleted(Object entity) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.softDeletedField == null)
      return;
    try {
      classMetaModel.softDeletedSetter.invoke(entity, Boolean.TRUE);
    } catch (Exception e) {
      throw new AuditingException("Could not set deleted status on entity [" + entity + "]", e);
    }
  }

  public boolean isDeleted(Object entity) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return false;
    if (classMetaModel.softDeletedField == null)
      return false;
    try {
      return (Boolean) classMetaModel.softDeletedGetter.invoke(entity);
    } catch (Exception e) {
      throw new AuditingException("Could not get deleted status on entity [" + entity + "]", e);
    }
  }

  public boolean isArchived(Object entity) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return false;
    if (classMetaModel.archivedField == null)
      return false;
    try {
      return (Boolean) classMetaModel.archivedGetter.invoke(entity);
    } catch (Exception e) {
      throw new AuditingException("Could not get archived status on entity [" + entity + "]", e);
    }
  }

  public long getVersionOf(Object entity) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return 0L;
    if (classMetaModel.versionField == null)
      return 0L;
    try {
      return (Long) classMetaModel.versionGetter.invoke(entity);
    } catch (Exception e) {
      throw new AuditingException("Could not get version of entity [" + entity + "]", e);
    }
  }

  public void setVersionOf(Object entity, Object[] state, String[] propertyNames, long version) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.versionSetter == null)
      return;
    try {
      classMetaModel.versionSetter.invoke(entity, version);
      state[findPropertyIndex(clazz, propertyNames, classMetaModel.versionField)] = version;
    } catch (Exception e) {
      throw new AuditingException("Could not set version on entity [" + entity + "]", e);
    }
  }

  public String getArchivedFieldName(Class<?> clazz) {
    ClassMetaModel classMetaModel = classMetaModelOf(findNonProxyClass(clazz));
    if (classMetaModel == null)
      return null;
    return classMetaModel.archivedField;
  }

  public void copyIdentity(Object oldVersion, Object newVersion) {
    Class<?> clazz = findNonProxyClass(oldVersion.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel.identityField == null)
      return;
    try {
      classMetaModel.identitySetter.invoke(newVersion, classMetaModel.identityGetter.invoke(oldVersion));
    } catch (Exception e) {
      throw new InternalVersioningException("Could not copy @Identity field [" + classMetaModel.identityField
          + "] from [" + oldVersion + "] to [" + newVersion + "]", e);
    }
  }

  public boolean isIdentityProperty(Object e, String propertyName) {
    Class<?> clazz = findNonProxyClass(e.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    return classMetaModel.identityField != null && classMetaModel.identityField.equals(propertyName);
  }

  public boolean isArchivedProperty(Object e, String propertyName) {
    Class<?> clazz = findNonProxyClass(e.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    return classMetaModel.archivedField != null && classMetaModel.archivedField.equals(propertyName);
  }

  public void setIdentity(Object e, Object identity) {
    Class<?> clazz = findNonProxyClass(e.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    try {
      classMetaModel.identitySetter.invoke(e, identity);
    } catch (Exception e1) {
      throw new InternalVersioningException(
          "Could not set @Identity field [" + classMetaModel.identityField + "] on [" + e + "] to [" + identity + "]",
          e1);
    }
  }

  public void copyCreatingUser(Object oldVersion, Object newVersion) {
    Class<?> clazz = findNonProxyClass(oldVersion.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel.creatingUserField == null)
      return;
    try {
      classMetaModel.creatingUserSetter.invoke(newVersion, classMetaModel.creatingUserGetter.invoke(oldVersion));
    } catch (Exception e) {
      throw new InternalVersioningException("Could not copy @CreatingUser field [" + classMetaModel.creatingUserField
          + "] from [" + oldVersion + "] to [" + newVersion + "]", e);
    }
  }

  public void copyCreationDate(Object oldVersion, Object newVersion) {
    Class<?> clazz = findNonProxyClass(oldVersion.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel.creationDateField == null)
      return;
    try {
      classMetaModel.creationDateSetter.invoke(newVersion, classMetaModel.creationDateGetter.invoke(oldVersion));
    } catch (Exception e) {
      throw new InternalVersioningException("Could not copy @CreationDate field [" + classMetaModel.creationDateField
          + "] from [" + oldVersion + "] to [" + newVersion + "]", e);
    }
  }

  public void ensureIdentity(Object entity, Object[] state, String[] propertyNames, EntityManager em) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel.identityField == null)
      return;
    try {
      Object identity = classMetaModel.identityGetter.invoke(entity);
      if (identity == null) {
        if (classMetaModel.identityGetter.getReturnType() == Long.class) {
          String seq = classMetaModel.identityAnnotation.sequence();
          BigInteger seqVal = (BigInteger) em.createNativeQuery("SELECT nextval('" + seq + "')").getSingleResult();
          identity = seqVal.longValue();
        } else {
          /* Create a new instance and set it */
          identity = classMetaModel.identityGetter.getReturnType().getDeclaredConstructor().newInstance();
          em.persist(identity);
        }
        classMetaModel.identitySetter.invoke(entity, identity);
        state[findPropertyIndex(clazz, propertyNames, classMetaModel.identityField)] = identity;
      }
    } catch (Exception e) {
      throw new InternalVersioningException(
          "Could not set initial @Identity field [" + classMetaModel.identityField + "] on [" + entity + "]", e);
    }
  }

  public boolean isVersioned(Class<?> clazz) {
    clazz = findNonProxyClass(clazz);
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return false;
    return classMetaModel.versioned;
  }

  public boolean isVersioned(Object entity) {
    Class<?> clazz = findNonProxyClass(entity.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return false;
    return classMetaModel.versioned;
  }

  public void incrementVersion(Object oldVersion, Object newVersion) {
    Class<?> clazz = findNonProxyClass(oldVersion.getClass());
    ClassMetaModel classMetaModel = classMetaModelOf(clazz);
    if (classMetaModel == null)
      return;
    if (classMetaModel.versionField == null)
      return;
    try {
      long oldVersionNumber = (Long) classMetaModel.versionGetter.invoke(oldVersion);
      classMetaModel.versionSetter.invoke(newVersion, oldVersionNumber + 1L);
    } catch (Exception e) {
      throw new AuditingException("Could not increment version of entity [" + newVersion + "]", e);
    }
  }

  public boolean isAssociationComposite(Class<?> ownerClass, String roleName) {
    /* The role name is the fully qualified class name + "." + fieldname */
    int lastDotIndex = roleName.lastIndexOf('.');
    String className = roleName.substring(0, lastDotIndex);
    String associationName = roleName.substring(lastDotIndex + 1);
    try {
      Class<?> clazz = findNonProxyClass(ownerClass.getClassLoader().loadClass(className));
      return findOutgoingAssociation(clazz, associationName) != null;
    } catch (ClassNotFoundException e) {
      throw new InternalVersioningException(
          "Could not find class [" + className + "] from PersistentCollection role [" + roleName + "]", e);
    }
  }
}
