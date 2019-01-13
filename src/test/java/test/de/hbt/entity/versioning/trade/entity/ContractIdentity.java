package test.de.hbt.entity.versioning.trade.entity;

import java.util.*;

import javax.persistence.*;

import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class ContractIdentity extends AbstractEntity {

  private String unversionedProperty;

  @OneToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<InternalNote> internalNotes = new ArrayList<>();
}
