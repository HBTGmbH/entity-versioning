package test.de.hbt.entity.versioning.trade.entity;

import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;
import test.de.hbt.entity.versioning.masterdata.entity.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class Contract extends AbstractVersionedEntity {

  @Identity
  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private ContractIdentity identity;

  @CreationDate
  @Temporal(TemporalType.TIMESTAMP)
  private Date creationDate;

  /** Just to test a to-one association of an unpersisted entity. */
  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private Something something;

  @ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private Something lazySomething;

  private String key;

  @ManyToOne
  @CascadeNewVersion
  private Quality quality;

  @ManyToOne
  @CascadeNewVersion
  private Quality latestQuality;

  @ManyToOne
  private ThirdParty customer;

  @ManyToOne
  private ThirdParty agent;

  @ManyToOne
  private ThirdParty seller;

  @ManyToOne
  private ThirdParty shipper;

  @ManyToOne
  private ThirdParty producer;

  @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<Shipment> shipments = new ArrayList<>();
}
