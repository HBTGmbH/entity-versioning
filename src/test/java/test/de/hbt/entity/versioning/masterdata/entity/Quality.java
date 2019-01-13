package test.de.hbt.entity.versioning.masterdata.entity;

import java.math.*;
import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;

@Entity
@ToString(callSuper = true)
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class Quality extends TradableItem {

  @Identity
  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private QualityIdentity identity;

  private String name;

  private String code;

  private String description;

  private BigDecimal altitude;

  private BigDecimal humidity;

  private String screen;

  @ManyToOne
  private FutureMarket futureMarket;

  @ManyToOne
  private Quality referenceQuality;

  @ManyToOne
  private ProcessingStage processingStage;

  @ManyToOne
  private Preparation preparation;

  @ManyToOne(fetch = FetchType.LAZY)
  private Origin origin;

  @ManyToOne(fetch = FetchType.LAZY)
  private Region region;

  @ManyToMany
  private List<Certification> certifications = new ArrayList<>();
}
