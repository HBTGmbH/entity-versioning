package test.de.hbt.entity.versioning.masterdata.entity;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class QualityAlias extends TradableItem {

  @Identity
  private Long identity;

  @ManyToOne
  private Quality quality;

  private String name;
}
