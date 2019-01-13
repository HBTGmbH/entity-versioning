package test.de.hbt.entity.versioning.masterdata.entity;

import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class Valuation extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  @Temporal(TemporalType.TIMESTAMP)
  private Date validFrom;

  @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<Relative> offsets = new ArrayList<>();

  @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<CropYear> cropYears = new ArrayList<>();
}
