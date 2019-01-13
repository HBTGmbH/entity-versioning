package test.de.hbt.entity.versioning.masterdata.entity;

import java.math.*;

import javax.persistence.*;

import lombok.*;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public @Data class Amount {

  private BigDecimal value;

  @ManyToOne
  private WeightUnit unit;
}
