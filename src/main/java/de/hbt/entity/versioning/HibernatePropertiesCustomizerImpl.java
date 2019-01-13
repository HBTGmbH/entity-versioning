package de.hbt.entity.versioning;

import java.util.*;

import org.hibernate.cfg.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.orm.jpa.*;
import org.springframework.stereotype.*;

/**
 * Used to set the {@link VersioningInterceptor} as Java object instance / Spring bean into the Hibernate properties map
 * which will be used internally by Spring when creating the JPA EntityManagerFactory.
 *
 * <p>
 * This is new as of Spring Boot 2.0.0.RC1 and was added with Spring Boot commit <a href=
 * "https://github.com/spring-projects/spring-boot/commit/59d5ed58428d8cb6c6d9fb723d0e334fe3e7d9be">#59d5ed58</a>
 */
@Component
class HibernatePropertiesCustomizerImpl implements HibernatePropertiesCustomizer {

  @Autowired
  private VersioningInterceptor versioningInterceptor;

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    /* As the interceptor as Spring bean */
    hibernateProperties.put(AvailableSettings.INTERCEPTOR, versioningInterceptor);
  }
}
