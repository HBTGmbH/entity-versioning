package test.de.hbt.entity.versioning.masterdata.entity;

import java.util.*;

import javax.persistence.*;

import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class QualityIdentity extends AbstractEntity {

  @OneToMany
  private List<Valuation> valuations = new ArrayList<>();
}
