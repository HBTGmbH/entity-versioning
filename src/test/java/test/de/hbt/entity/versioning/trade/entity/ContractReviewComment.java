package test.de.hbt.entity.versioning.trade.entity;

import java.util.*;

import javax.persistence.*;

import de.hbt.entity.versioning.annotations.*;
import lombok.*;
import test.de.hbt.entity.versioning.common.*;

@Entity
@NamedQueries({
    @NamedQuery(name = ContractReviewComment.FIND_ALL_BY_CONTRACT_NUMBER, query = "FROM ContractReviewComment WHERE contract.key = :contractNumber order by reviewedAt DESC") })
@EqualsAndHashCode(of = {}, callSuper = true)
public @Data class ContractReviewComment extends AbstractVersionedEntity {

  public static final String FIND_ALL_BY_CONTRACT_NUMBER = "ContractsReviewComments.findAllByContractNumber";

  @Identity
  private Long identity;

  @ManyToOne
  @CascadeNewVersion
  private Contract contract;

  private Date reviewedAt;

  private String reviewer;

  private String comment;
}
