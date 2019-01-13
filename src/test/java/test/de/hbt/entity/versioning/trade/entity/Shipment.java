package test.de.hbt.entity.versioning.trade.entity;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;
import test.de.hbt.entity.versioning.masterdata.entity.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class Shipment extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private int amount;

  @AttributeOverride(name = "value", column = @Column(name = "sum_quality_amount_value"))
  @AssociationOverride(name = "unit", joinColumns = @JoinColumn(name = "sum_quality_amount_unit_id"))
  private Amount sumQualityAmount;
}
