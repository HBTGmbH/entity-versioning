package test.de.hbt.entity.versioning.masterdata.entity;

import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class ValuationEntry extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  @Temporal(TemporalType.TIMESTAMP)
  private Date validFrom;
}
