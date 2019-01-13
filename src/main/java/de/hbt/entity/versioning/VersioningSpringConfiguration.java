package de.hbt.entity.versioning;

import org.springframework.context.annotation.*;

/**
 * Spring Configuration to be used with {@link Import} in order to enable the versioning library.
 */
@Configuration
@Import({ MetaModel.class, VersioningInterceptor.class, HibernatePropertiesCustomizerImpl.class,
    VersioningComponent.class })
public class VersioningSpringConfiguration {
}
