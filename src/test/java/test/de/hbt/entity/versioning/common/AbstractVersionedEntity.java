package test.de.hbt.entity.versioning.common;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import de.hbt.entity.versioning.annotations.Version;
import lombok.*;

@MappedSuperclass
@EqualsAndHashCode(of = {}, callSuper = true)
public abstract @Data class AbstractVersionedEntity extends AbstractEntity {

  @Archived
  @Column(updatable = false)
  protected boolean archived;

  @Version
  @Column(updatable = false)
  protected long version;
}
