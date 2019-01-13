package test.de.hbt.entity.versioning.masterdata.entity;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class Origin extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private String name;

  @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<Region> regions = new ArrayList<>();
}
