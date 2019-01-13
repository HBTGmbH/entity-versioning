package test.de.hbt.entity.versioning.masterdata.entity;

import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class CropYear extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private String code;

  private String name;

  @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<ValuationEntry> valuationEntries = new ArrayList<>();
}
