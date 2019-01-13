package test.de.hbt.entity.versioning.masterdata.entity;

import java.math.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class FutureMarket extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private String abbreviation;

  private String name;

  private String alias;

  @ManyToOne
  private WeightUnit standardWeightUnit;

  private BigDecimal lotSize;

}
