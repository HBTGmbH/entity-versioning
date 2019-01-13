package de.hbt.entity.versioning;

import static de.hbt.entity.versioning.ClassUtils.*;

import javax.persistence.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.*;

/** Spring component providing a public API into the versioning framework. */
@Component
public class VersioningComponent {

  @Lazy
  @Autowired
  private EntityManager entityManager;

  @Autowired
  private VersioningInterceptor versioningInterceptor;

  @Autowired
  private MetaModel metaModel;

  public boolean hasEntityChanged(Object entity) {
    if (!entityManager.contains(entity))
      return false;
    org.hibernate.engine.spi.SessionImplementor si = entityManager
        .unwrap(org.hibernate.engine.spi.SessionImplementor.class);
    VersioningContext context = versioningInterceptor.versioningContextFor(si.getTransaction());
    Class<?> realClass = findNonProxyClass(entity.getClass());
    EntityKey key = new EntityKey(realClass, metaModel.getIdOf(entity));
    return context.newVersionMapping.containsValue(key) && !key.equals(context.newVersionMapping.get(key));
  }
}
