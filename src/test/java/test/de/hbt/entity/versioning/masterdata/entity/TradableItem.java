package test.de.hbt.entity.versioning.masterdata.entity;

import javax.persistence.*;

import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public abstract @Data class TradableItem extends AbstractVersionedEntity {

  @Column(name = "type", insertable = false, updatable = false)
  protected TradableItemType contractType;
}
