package test.de.hbt.entity.versioning.trade.entity;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class Allocation extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  @ManyToOne
  @CascadeNewVersion(withDelete = true)
  private Contract purchaseContract;

  @ManyToOne
  @CascadeNewVersion(withDelete = true)
  private Contract salesContract;
}
