package de.hbt.entity.versioning;

import java.lang.reflect.*;

import de.hbt.entity.versioning.exception.*;
import lombok.experimental.*;

@UtilityClass
class ClassUtils {

  /**
   * Determine the name of a getter method for the given property.
   *
   * <p>
   * This follows the Java Beans specification by using the 'get' prefix for any return type other than boolean and
   * using the 'is' prefix for boolean. Additionally, camel-case is used in the final name.
   *
   * @param propertyName the property name
   * @param clazz        the type of the property
   * @return the getter name
   */
  private static String getterName(String propertyName, Class<?> clazz) {
    String part = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
    if (clazz == boolean.class) {
      return "is" + part;
    } else {
      return "get" + part;
    }
  }

  /**
   * Infer the setter name from the given property/field name.
   *
   * <p>
   * This follows the Java Beans specification by using the 'set' prefix and using camel-case.
   *
   * @param propertyName the property name
   * @return the setter name
   */
  private static String setterName(String propertyName) {
    return "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
  }

  /**
   * Find a getter method for the given Java class member.
   *
   * @param member the Java class member to find the getter method for
   * @return the getter {@link Method}
   */
  static Method findGetter(Member member) {
    Class<?> clazz = member.getDeclaringClass();
    String getterName = getterName(member.getName(), ((Field) member).getType());
    try {
      return clazz.getDeclaredMethod(getterName);
    } catch (Exception e) {
      throw new InternalVersioningException(
          "Could not find getter method for [" + member.getName() + "] in [" + clazz + "]", e);
    }
  }

  /**
   * Find a setter method for the given Java class member.
   *
   * @param member the Java class member to find the setter method for
   * @return the setter {@link Method}
   */
  static Method findSetter(Member member) {
    Class<?> clazz = member.getDeclaringClass();
    String setterName = setterName(member.getName());
    try {
      return clazz.getDeclaredMethod(setterName, ((Field) member).getType());
    } catch (Exception e) {
      throw new InternalVersioningException(
          "Could not find setter method for [" + member.getName() + "] in [" + clazz + "]", e);
    }
  }

  /**
   * Hibernate generates proxy classes and we have to traverse the type hierarchy to their actual/real parent class.
   *
   * @param clazz the class which might be a Hibernate-generated proxy class
   * @return the actual class, which may either be the given <code>clazz</code> already (if it is not a Hibernate proxy
   *         class) or the first non-proxy super class in its type hierarchy
   */
  @SuppressWarnings("unchecked")
  static <T> Class<T> findNonProxyClass(Class<? extends T> clazz) {
    // Obviously, checking only for the class name is not very good, but I don't see
    // any other way.
    if (clazz.getName().contains("_$$_") || clazz.getName().contains("$HibernateProxy$"))
      return (Class<T>) findNonProxyClass(clazz.getSuperclass());
    return (Class<T>) clazz;
  }
}
