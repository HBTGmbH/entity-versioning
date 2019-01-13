package test.de.hbt.entity.versioning.masterdata.entity;

import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class CustomerProfile extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private String type;

  private String category;

  @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinTable(inverseJoinColumns = @JoinColumn(name = "third_party_contract_preferences_id"))
  private List<ThirdPartyContractPreferences> contractPreferences = new ArrayList<>();
}
