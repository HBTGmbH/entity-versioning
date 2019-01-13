package test.de.hbt.entity.versioning.masterdata.entity;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class ThirdParty extends AbstractVersionedEntity {

  @Identity
  private Long identity;

  private String description;

  private String language;

  private String moreInfoLink;

  private String clientId;

  private String commercialName;

  private String tagetikCode;

  private String legalName;

  private String acronym;

  @ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private CustomerProfile customerProfile;
}
