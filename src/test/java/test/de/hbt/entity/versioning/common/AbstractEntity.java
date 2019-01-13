package test.de.hbt.entity.versioning.common;

import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.*;
import org.hibernate.annotations.Parameter;
import org.hibernate.id.enhanced.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;

@MappedSuperclass
public abstract @Data class AbstractEntity {

  @Id
  @GeneratedValue(generator = "SequencePerEntityGenerator")
  @GenericGenerator(name = "SequencePerEntityGenerator", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
      @Parameter(name = SequenceStyleGenerator.CONFIG_PREFER_SEQUENCE_PER_ENTITY, value = "true"),
      @Parameter(name = SequenceStyleGenerator.CONFIG_SEQUENCE_PER_ENTITY_SUFFIX, value = "_id_seq") })
  protected Long id;

  @SoftDeleted
  protected boolean deleted;

  @Column(updatable = false)
  @ModifyingUser
  protected String createdBy;

  @Column(updatable = false)
  @CreatingUser
  protected String createdByOrig;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(updatable = false)
  @ModificationDate
  protected Date createdAt;

  public int hashCode() {
    return System.identityHashCode(this);
  }

  public boolean equals(Object other) {
    return this == other;
  }
}
