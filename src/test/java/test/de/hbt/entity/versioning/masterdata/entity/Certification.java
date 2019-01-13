package test.de.hbt.entity.versioning.masterdata.entity;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
@NoArgsConstructor
public @Data class Certification extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private String name;

  public Certification(Long id) {
    this.id = id;
  }
}
