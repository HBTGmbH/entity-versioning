package test.de.hbt.entity.versioning;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.persistence.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import de.hbt.entity.versioning.*;
import de.hbt.entity.versioning.exception.*;
import test.de.hbt.entity.versioning.masterdata.*;
import test.de.hbt.entity.versioning.masterdata.entity.*;
import test.de.hbt.entity.versioning.trade.*;
import test.de.hbt.entity.versioning.trade.entity.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestConfiguration.class)
@ActiveProfiles("test")
@Import({ MasterdataService.class, ContractService.class })
class AllTests extends AbstractTest {

  @Autowired
  private MasterdataService masterdataService;

  @Autowired
  private ContractService contractService;

  @Autowired
  private VersioningComponent versioningComponent;

  @ParameterizedTest
  @CsvSource({ "false", "true" })
  void shouldCreateIdentity(boolean shouldClearForAssertion) {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Quality q = new Quality();
        q.setName("Quality Amount");
        entityManager.persist(q);
        /* Assert that the identity exists directly after the persist */
        assertThat(q.getIdentity()).isNotNull();
        put("identityId", q.getIdentity().getId());
      }
    });
    long identityId = getId("identityId");
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + QualityIdentity.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(1L);
        QualityIdentity identity = entityManager.find(QualityIdentity.class, identityId);
        assertThat(identity).isNotNull();
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void shouldNotDetectChangeOfNonUpdatableIdentityEntity(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        entityManager.persist(c);
        put("contractId", c.getId());
      }
    });
    long contractId = getId("contractId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, contractId);
        c.setIdentity(new ContractIdentity());
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + ContractIdentity.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(1L);
        Contract contract = entityManager.find(Contract.class, contractId);
        assertThat(contract.getIdentity()).isNotNull();
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void shouldNotDetectChangeOfNonUpdatableIdentity(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        WeightUnit w = new WeightUnit();
        entityManager.persist(w);
        put("weightUnitId", w.getId(), "identity", w.getIdentity());
      }
    });
    long weightUnitId = getId("weightUnitId");
    long identity = getId("identity");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        WeightUnit w = entityManager.find(WeightUnit.class, weightUnitId);
        w.setIdentity(12345678L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + WeightUnit.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(1L);
        WeightUnit w = entityManager.find(WeightUnit.class, weightUnitId);
        assertThat(w.getIdentity()).isEqualTo(identity);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false", "true" })
  void shouldSetCreationDate(boolean shouldClearForAssertion) {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        c.setKey("Amount");
        entityManager.persist(c);
        /* Assert that the creation date exists directly after the persist */
        assertThat(c.getCreationDate().getTime()).isEqualTo(nowSupplier.get().toEpochMilli());
        put("contractId", c.getId());
      }
    });
    long contractId = getId("contractId");
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(1L);
        Contract c = entityManager.find(Contract.class, contractId);
        assertThat(c).isNotNull();
        assertThat(c.getCreationDate().getTime()).isEqualTo(nowSupplier.get().toEpochMilli());
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void shouldKeepCreationDate(boolean shouldClear, boolean shouldClearForAssertion) {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        c.setKey("Amount");
        entityManager.persist(c);
        /* Assert that the creation date exists directly after the persist */
        assertThat(c.getCreationDate().getTime()).isEqualTo(nowSupplier.get().toEpochMilli());
        put("contractId", c.getId());
      }
    });
    long contractId = getId("contractId");
    if (shouldClear)
      entityManager.clear();
    /* Modification */
    long originalCreationDate = nowSupplier.get().toEpochMilli();
    when(nowSupplier.get()).thenReturn(new Date(123L).toInstant());
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, contractId);
        c.setKey("Amount (modified)");
        /* Modify the creation date (should not be possible -> will not be persisted) */
        c.setCreationDate(new Date());
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Contract c0 = entityManager.find(Contract.class, contractId);
        Contract c1 = entityManager.find(Contract.class, contractId + 1L);
        assertThat(c0.getCreationDate().getTime()).isEqualTo(originalCreationDate);
        assertThat(c0.getCreatedAt().getTime()).isEqualTo(originalCreationDate);
        assertThat(c0.getKey()).isEqualTo("Amount");
        assertThat(c1.getCreationDate().getTime()).isEqualTo(originalCreationDate);
        assertThat(c1.getCreatedAt().getTime()).isEqualTo(123L);
        assertThat(c1.getKey()).isEqualTo("Amount (modified)");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false", "true" })
  void shouldSetCreatingUser(boolean shouldClearForAssertion) {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Quality q = new Quality();
        q.setName("Quality Amount");
        entityManager.persist(q);
        /* Assert that the creating user exists directly after the persist */
        assertThat(q.getCreatedByOrig()).isEqualTo("testuser");
        put("qualityId", q.getId());
      }
    });
    long qualityId = getId("qualityId");
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Quality.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(1L);
        Quality q = entityManager.find(Quality.class, qualityId);
        assertThat(q).isNotNull();
        assertThat(q.getCreatedByOrig()).isEqualTo("testuser");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void shouldKeepCreatingUser(boolean shouldClear, boolean shouldClearForAssertion) {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Quality q = new Quality();
        q.setName("Quality Amount");
        entityManager.persist(q);
        /* Assert that the creating user exists directly after the persist */
        assertThat(q.getCreatedByOrig()).isEqualTo("testuser");
        put("qualityId", q.getId());
      }
    });
    long qualityId = getId("qualityId");
    if (shouldClear)
      entityManager.clear();
    /* Modification */
    when(userSupplier.get()).thenReturn(() -> "anotheruser");
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Quality q = entityManager.find(Quality.class, qualityId);
        q.setName("Quality Amount (modified)");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Quality q0 = entityManager.find(Quality.class, qualityId);
        Quality q1 = entityManager.find(Quality.class, qualityId + 1L);
        assertThat(q0.getCreatedByOrig()).isEqualTo("testuser");
        assertThat(q0.getCreatedBy()).isEqualTo("testuser");
        assertThat(q0.getName()).isEqualTo("Quality Amount");
        assertThat(q1.getCreatedByOrig()).isEqualTo("testuser");
        assertThat(q1.getCreatedBy()).isEqualTo("anotheruser");
        assertThat(q1.getName()).isEqualTo("Quality Amount (modified)");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void shouldKeepCreatingUserEvenIfModified(boolean shouldClear, boolean shouldClearForAssertion) {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Quality q = new Quality();
        q.setName("Quality Amount");
        entityManager.persist(q);
        /* Assert that the creating user exists directly after the persist */
        assertThat(q.getCreatedByOrig()).isEqualTo("testuser");
        put("qualityId", q.getId());
      }
    });
    long qualityId = getId("qualityId");
    if (shouldClear)
      entityManager.clear();
    /* Modification */
    when(userSupplier.get()).thenReturn(() -> "anotheruser");
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Quality q = entityManager.find(Quality.class, qualityId);
        q.setName("Quality Amount (modified)");
        q.setCreatedBy("unexpecteduser");
        q.setCreatedByOrig("unexpecteduser");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Quality q0 = entityManager.find(Quality.class, qualityId);
        Quality q1 = entityManager.find(Quality.class, qualityId + 1L);
        assertThat(q0.getCreatedByOrig()).isEqualTo("testuser");
        assertThat(q0.getCreatedBy()).isEqualTo("testuser");
        assertThat(q0.getName()).isEqualTo("Quality Amount");
        assertThat(q1.getCreatedByOrig()).isEqualTo("testuser");
        assertThat(q1.getCreatedBy()).isEqualTo("anotheruser");
        assertThat(q1.getName()).isEqualTo("Quality Amount (modified)");
      }
    });
  }

  @Test
  void oneNewSaveWithUnexpectedVersion() {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Region r = new Region();
        r.setVersion(1L); // <- INCORRECT!
        assertThatExceptionOfType(UnexpectedVersionException.class).isThrownBy(() -> entityManager.persist(r));
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void oneEntityScalarChange(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        put("regionId", regionId);
      }
    });
    long regionId = getId("regionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        entityManager.flush();
        assertThat(r.getId()).isEqualTo(regionId + 1L);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getCreatedBy()).isNotNull();
        assertThat(r.isArchived()).isFalse();
        assertThat(r.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region newRegion = entityManager.find(Region.class, regionId + 1L);
        assertThat(newRegion.getCreatedAt()).isNotNull();
        assertThat(newRegion.getCreatedBy()).isNotNull();
        assertThat(newRegion.isArchived()).isFalse();
        assertThat(newRegion.getVersion()).isEqualTo(2L);
        assertThat(newRegion.getName()).isEqualTo("Region Amount (modified)");
        Region oldRegion = entityManager.find(Region.class, regionId);
        assertThat(oldRegion.getCreatedAt()).isNotNull();
        assertThat(oldRegion.getCreatedBy()).isNotNull();
        assertThat(oldRegion.isArchived()).isTrue();
        assertThat(oldRegion.getVersion()).isEqualTo(1L);
        assertThat(oldRegion.getName()).isEqualTo("Region Amount");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false", "true" })
  void oneEntityHasChanged(boolean shouldClear) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region e = new Region();
        e.setName("Region Amount");
        entityManager.persist(e);
        put("regionId", e.getId());
        assertThat(versioningComponent.hasEntityChanged(e)).isFalse();
      }
    });
    long regionId = getId("regionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification and assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        entityManager.flush();
        assertThat(versioningComponent.hasEntityChanged(r)).isTrue();
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void updateToOneAssociationFromNullToNotNull(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        entityManager.persist(c);
        put("contractId", c.getId());
      }
    });
    long contractId = getId("contractId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, contractId);
        Something st = new Something();
        st.setText("Amount");
        c.setSomething(st);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Something.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(1L);
        Contract oldContract = entityManager.find(Contract.class, contractId);
        Contract newContract = entityManager.find(Contract.class, contractId + 1L);
        assertThat(oldContract.getSomething()).isNull();
        assertThat(newContract.getSomething().getText()).isEqualTo("Amount");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void noUpdateToOneLazyAssociation(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        Something sth = new Something();
        sth.setText("Something");
        c.setLazySomething(sth);
        entityManager.persist(c);
        put("contractId", c.getId());
      }
    });
    long contractId = getId("contractId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        entityManager.find(Contract.class, contractId);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Something.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(1L);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void updateToOneLazyAssociationWithFind(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        Something sth = new Something();
        sth.setText("Something");
        c.setLazySomething(sth);
        entityManager.persist(c);
        put("contractId", c.getId());
      }
    });
    long contractId = getId("contractId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, contractId);
        c.getLazySomething().setText("Something (modified)");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Something.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(2L);
        Contract oldContract = entityManager.find(Contract.class, contractId);
        Contract newContract = entityManager.find(Contract.class, contractId + 1L);
        assertThat(oldContract.getLazySomething().getText()).isEqualTo("Something");
        assertThat(newContract.getLazySomething().getText()).isEqualTo("Something (modified)");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void updateEntityInsideOfLazyAssociation(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        ThirdParty tp = new ThirdParty();
        tp.setClientId("123");
        CustomerProfile cp = new CustomerProfile();
        tp.setCustomerProfile(cp);
        ThirdPartyContractPreferences tpcp = new ThirdPartyContractPreferences();
        tpcp.setName("Preference");
        cp.getContractPreferences().add(tpcp);
        entityManager.persist(tp);
        put("tpId", tp.getId(), "cpId", tp.getCustomerProfile().getId(), "cprefId", tpcp.getId());
      }
    });
    long tpId = getId("tpId");
    long cpId = getId("cpId");
    long cprefId = getId("cprefId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        ThirdParty tp = entityManager.find(ThirdParty.class, tpId);
        tp.getCustomerProfile().getContractPreferences().get(0).setName("Preference (modified)");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + ThirdParty.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + CustomerProfile.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + ThirdPartyContractPreferences.class.getSimpleName() + " e",
                Long.class)
            .getSingleResult()).isEqualTo(2L);
        ThirdParty oldTp = entityManager.find(ThirdParty.class, tpId);
        assertThat(oldTp.getCustomerProfile().getId()).isEqualTo(cpId);
        assertThat(oldTp.getCustomerProfile().getContractPreferences()).hasSize(1);
        assertThat(oldTp.getCustomerProfile().getContractPreferences().get(0).getId()).isEqualTo(cprefId);
        assertThat(oldTp.getCustomerProfile().getContractPreferences().get(0).getName()).isEqualTo("Preference");
        ThirdParty newTp = entityManager.find(ThirdParty.class, tpId + 1L);
        assertThat(newTp.getCustomerProfile().getId()).isEqualTo(cpId + 1L);
        assertThat(newTp.getCustomerProfile().getContractPreferences()).hasSize(1);
        assertThat(newTp.getCustomerProfile().getContractPreferences().get(0).getId()).isEqualTo(cprefId + 1L);
        assertThat(newTp.getCustomerProfile().getContractPreferences().get(0).getName())
            .isEqualTo("Preference (modified)");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void oneEntityDelete(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        put("regionId", regionId);
      }
    });
    long regionId = getId("regionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region r = entityManager.find(Region.class, regionId);
        r.setDeleted(true);
        entityManager.flush();
        assertThat(r.getId()).isEqualTo(regionId + 1L);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getCreatedBy()).isNotNull();
        assertThat(r.isArchived()).isFalse();
        assertThat(r.isDeleted()).isTrue();
        assertThat(r.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region newRegion = entityManager.find(Region.class, regionId + 1L);
        assertThat(newRegion.getCreatedAt()).isNotNull();
        assertThat(newRegion.getCreatedBy()).isNotNull();
        assertThat(newRegion.isArchived()).isFalse();
        assertThat(newRegion.isDeleted()).isTrue();
        assertThat(newRegion.getName()).isEqualTo("Region Amount");
        Region oldRegion = entityManager.find(Region.class, regionId);
        assertThat(oldRegion.getCreatedAt()).isNotNull();
        assertThat(oldRegion.getCreatedBy()).isNotNull();
        assertThat(oldRegion.isArchived()).isTrue();
        assertThat(oldRegion.isDeleted()).isFalse();
        assertThat(oldRegion.getName()).isEqualTo("Region Amount");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void noTwoVersionsOfInitialEntityScalarChange(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        long regionId = masterdataService.createNewRegion("Region Amount");
        entityManager.flush();
        if (shouldClear)
          entityManager.clear();
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        entityManager.flush();
        if (shouldClearForAssertion)
          entityManager.clear();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(1L);
        assertThat(r.getId()).isEqualTo(regionId);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getCreatedBy()).isNotNull();
        assertThat(r.isArchived()).isFalse();
        assertThat(r.getVersion()).isEqualTo(1L);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void twoEntitiesVersionCascade(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        c.setKey("P01");
        ContractReviewComment crc = new ContractReviewComment();
        crc.setContract(c);
        entityManager.persist(c);
        entityManager.persist(crc);
        put("cId", c.getId(), "crcId", crc.getId());
      }
    });
    long cId = getId("cId");
    long crcId = getId("crcId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, cId);
        c.setCreationDate(new Date());
        c.setKey("P01 (modified)");
        entityManager.flush();
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + ContractReviewComment.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Contract oldContract = entityManager.find(Contract.class, cId);
        assertThat(oldContract.getCreatedAt()).isNotNull();
        assertThat(oldContract.getCreatedBy()).isNotNull();
        assertThat(oldContract.isArchived()).isTrue();
        assertThat(oldContract.getVersion()).isEqualTo(1L);
        assertThat(oldContract.getKey()).isEqualTo("P01");
        Contract newContract = entityManager.find(Contract.class, cId + 1L);
        assertThat(newContract.getCreatedAt()).isNotNull();
        assertThat(newContract.getCreatedBy()).isNotNull();
        assertThat(newContract.isArchived()).isFalse();
        assertThat(newContract.getVersion()).isEqualTo(2L);
        assertThat(newContract.getKey()).isEqualTo("P01 (modified)");
        ContractReviewComment oldCrc = entityManager.find(ContractReviewComment.class, crcId);
        assertThat(oldCrc.getCreatedAt()).isNotNull();
        assertThat(oldCrc.getCreatedBy()).isNotNull();
        assertThat(oldCrc.isArchived()).isTrue();
        assertThat(oldCrc.getVersion()).isEqualTo(1L);
        assertThat(oldCrc.getContract()).isEqualTo(oldContract);
        ContractReviewComment newCrc = entityManager.find(ContractReviewComment.class, crcId + 1L);
        assertThat(newCrc.getCreatedAt()).isNotNull();
        assertThat(newCrc.getCreatedBy()).isNotNull();
        assertThat(newCrc.isArchived()).isFalse();
        assertThat(newCrc.getVersion()).isEqualTo(2L);
        assertThat(newCrc.getContract()).isEqualTo(newContract);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void parentChildScalarVersionCascade(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        put("regionId", regionId, "originId", originId);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        entityManager.flush();
        assertThat(r.getId()).isEqualTo(regionId + 1L);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getCreatedBy()).isNotNull();
        assertThat(r.isArchived()).isFalse();
        assertThat(r.getVersion()).isEqualTo(2L);
        assertThat(r.getName()).isEqualTo("Region Amount (modified)");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region oldRegion = entityManager.find(Region.class, regionId);
        assertThat(oldRegion.getCreatedAt()).isNotNull();
        assertThat(oldRegion.getCreatedBy()).isNotNull();
        assertThat(oldRegion.isArchived()).isTrue();
        assertThat(oldRegion.getVersion()).isEqualTo(1L);
        assertThat(oldRegion.getName()).isEqualTo("Region Amount");
        Region newRegion = entityManager.find(Region.class, regionId + 1L);
        assertThat(newRegion.getCreatedAt()).isNotNull();
        assertThat(newRegion.getCreatedBy()).isNotNull();
        assertThat(newRegion.isArchived()).isFalse();
        assertThat(newRegion.getVersion()).isEqualTo(2L);
        assertThat(newRegion.getName()).isEqualTo("Region Amount (modified)");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(oldRegion);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(newOrigin.getRegions()).containsExactly(newRegion);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void twoEntitiesCascadeDelete(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long salesContractId = masterdataService.createNewContract("S1");
        long purchaseContractId = masterdataService.createNewContract("P1");
        long allocationId = masterdataService.createNewAllocation(entityManager.find(Contract.class, salesContractId),
            entityManager.find(Contract.class, purchaseContractId));
        put("salesContractId", salesContractId, "purchaseContractId", purchaseContractId, "allocationId", allocationId);
      }
    });
    long salesContractId = getId("salesContractId");
    long purchaseContractId = getId("purchaseContractId");
    long allocationId = getId("allocationId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, salesContractId);
        c.setDeleted(true);
        entityManager.flush();
        assertThat(c.getId()).isEqualTo(salesContractId + 1L + 1L);
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getCreatedBy()).isNotNull();
        assertThat(c.isArchived()).isFalse();
        assertThat(c.getVersion()).isEqualTo(2L);
        assertThat(c.getKey()).isEqualTo("S1");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(3L);
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Allocation.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(2L);
        Contract oldSalesContract = entityManager.find(Contract.class, salesContractId);
        assertThat(oldSalesContract.getCreatedAt()).isNotNull();
        assertThat(oldSalesContract.getCreatedBy()).isNotNull();
        assertThat(oldSalesContract.isArchived()).isTrue();
        assertThat(oldSalesContract.isDeleted()).isFalse();
        assertThat(oldSalesContract.getVersion()).isEqualTo(1L);
        assertThat(oldSalesContract.getKey()).isEqualTo("S1");
        Contract newSalesContract = entityManager.find(Contract.class, salesContractId + 1L + 1L);
        assertThat(newSalesContract.getCreatedAt()).isNotNull();
        assertThat(newSalesContract.getCreatedBy()).isNotNull();
        assertThat(newSalesContract.isArchived()).isFalse();
        assertThat(newSalesContract.isDeleted()).isTrue();
        assertThat(newSalesContract.getVersion()).isEqualTo(2L);
        assertThat(newSalesContract.getKey()).isEqualTo("S1");
        Contract newPurchaseContract = entityManager.find(Contract.class, purchaseContractId);
        assertThat(newPurchaseContract.getCreatedAt()).isNotNull();
        assertThat(newPurchaseContract.getCreatedBy()).isNotNull();
        assertThat(newPurchaseContract.isArchived()).isFalse();
        assertThat(newPurchaseContract.isDeleted()).isFalse();
        assertThat(newPurchaseContract.getVersion()).isEqualTo(1L);
        assertThat(newPurchaseContract.getKey()).isEqualTo("P1");
        Allocation oldAllocation = entityManager.find(Allocation.class, allocationId);
        assertThat(oldAllocation.getCreatedAt()).isNotNull();
        assertThat(oldAllocation.getCreatedBy()).isNotNull();
        assertThat(oldAllocation.isArchived()).isTrue();
        assertThat(oldAllocation.isDeleted()).isFalse();
        assertThat(oldAllocation.getVersion()).isEqualTo(1L);
        assertThat(oldAllocation.getSalesContract()).isEqualTo(entityManager.find(Contract.class, salesContractId));
        assertThat(oldAllocation.getPurchaseContract())
            .isEqualTo(entityManager.find(Contract.class, purchaseContractId));
        Allocation newAllocation = entityManager.find(Allocation.class, allocationId + 1L);
        assertThat(newAllocation.getCreatedAt()).isNotNull();
        assertThat(newAllocation.getCreatedBy()).isNotNull();
        assertThat(newAllocation.isArchived()).isFalse();
        assertThat(newAllocation.isDeleted()).isTrue();
        assertThat(newAllocation.getVersion()).isEqualTo(2L);
        assertThat(newAllocation.getSalesContract())
            .isEqualTo(entityManager.find(Contract.class, salesContractId + 1L + 1L));
        assertThat(newAllocation.getPurchaseContract())
            .isEqualTo(entityManager.find(Contract.class, purchaseContractId));
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void twoEntitiesTwoAssociationsCascadeDelete(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long salesContractId = masterdataService.createNewContract("S1");
        long purchaseContractId = masterdataService.createNewContract("P1");
        long allocationId = masterdataService.createNewAllocation(entityManager.find(Contract.class, salesContractId),
            entityManager.find(Contract.class, purchaseContractId));
        put("salesContractId", salesContractId, "purchaseContractId", purchaseContractId, "allocationId", allocationId);
      }
    });
    long salesContractId = getId("salesContractId");
    long purchaseContractId = getId("purchaseContractId");
    long allocationId = getId("allocationId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, salesContractId);
        c.setDeleted(true);
        c = entityManager.find(Contract.class, purchaseContractId);
        c.setDeleted(true);
        entityManager.flush();
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getCreatedBy()).isNotNull();
        assertThat(c.isArchived()).isFalse();
        assertThat(c.getVersion()).isEqualTo(2L);
        assertThat(c.getKey()).isEqualTo("P1");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(4L);
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Allocation.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(2L);
        Contract oldSalesContract = entityManager.find(Contract.class, salesContractId);
        assertThat(oldSalesContract.getCreatedAt()).isNotNull();
        assertThat(oldSalesContract.getCreatedBy()).isNotNull();
        assertThat(oldSalesContract.isArchived()).isTrue();
        assertThat(oldSalesContract.isDeleted()).isFalse();
        assertThat(oldSalesContract.getVersion()).isEqualTo(1L);
        assertThat(oldSalesContract.getKey()).isEqualTo("S1");
        Contract newSalesContract = entityManager
            .createQuery("FROM " + Contract.class.getSimpleName() + " WHERE key = :key AND version = 2", Contract.class)
            .setParameter("key", "S1").getSingleResult();
        assertThat(newSalesContract.getCreatedAt()).isNotNull();
        assertThat(newSalesContract.getCreatedBy()).isNotNull();
        assertThat(newSalesContract.isArchived()).isFalse();
        assertThat(newSalesContract.isDeleted()).isTrue();
        Contract oldPurchaseContract = entityManager.find(Contract.class, purchaseContractId);
        assertThat(oldPurchaseContract.getCreatedAt()).isNotNull();
        assertThat(oldPurchaseContract.getCreatedBy()).isNotNull();
        assertThat(oldPurchaseContract.isArchived()).isTrue();
        assertThat(oldPurchaseContract.isDeleted()).isFalse();
        assertThat(oldPurchaseContract.getVersion()).isEqualTo(1L);
        assertThat(oldPurchaseContract.getKey()).isEqualTo("P1");
        Contract newPurchaseContract = entityManager
            .createQuery("FROM " + Contract.class.getSimpleName() + " WHERE key = :key AND version = 2", Contract.class)
            .setParameter("key", "P1").getSingleResult();
        assertThat(newPurchaseContract.getCreatedAt()).isNotNull();
        assertThat(newPurchaseContract.getCreatedBy()).isNotNull();
        assertThat(newPurchaseContract.isArchived()).isFalse();
        assertThat(newPurchaseContract.isDeleted()).isTrue();
        Allocation oldAllocation = entityManager.find(Allocation.class, allocationId);
        assertThat(oldAllocation.getCreatedAt()).isNotNull();
        assertThat(oldAllocation.getCreatedBy()).isNotNull();
        assertThat(oldAllocation.isArchived()).isTrue();
        assertThat(oldAllocation.isDeleted()).isFalse();
        assertThat(oldAllocation.getVersion()).isEqualTo(1L);
        assertThat(oldAllocation.getSalesContract()).isEqualTo(oldSalesContract);
        assertThat(oldAllocation.getPurchaseContract()).isEqualTo(oldPurchaseContract);
        Allocation newAllocation = entityManager.find(Allocation.class, allocationId + 1L);
        assertThat(newAllocation.getCreatedAt()).isNotNull();
        assertThat(newAllocation.getCreatedBy()).isNotNull();
        assertThat(newAllocation.isArchived()).isFalse();
        assertThat(newAllocation.isDeleted()).isTrue();
        assertThat(newAllocation.getVersion()).isEqualTo(2L);
        assertThat(newAllocation.getSalesContract()).isEqualTo(newSalesContract);
        assertThat(newAllocation.getPurchaseContract()).isEqualTo(newPurchaseContract);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void twoEntitiesSingleCollectionVersionCascade(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        put("regionId", regionId, "originId", originId);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        long regionId2 = masterdataService.createNewRegion("Region B");
        put("regionId2", regionId2);
        o.getRegions().add(entityManager.find(Region.class, regionId2));
        entityManager.flush();
        assertThat(o.getId()).isEqualTo(originId + 1L);
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getCreatedBy()).isNotNull();
        assertThat(o.isArchived()).isFalse();
        assertThat(o.getVersion()).isEqualTo(2L);
      }
    });
    long regionId2 = getId("regionId2");
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region regionA = entityManager.find(Region.class, regionId);
        assertThat(regionA.getCreatedAt()).isNotNull();
        assertThat(regionA.getCreatedBy()).isNotNull();
        assertThat(regionA.isArchived()).isFalse();
        assertThat(regionA.getVersion()).isEqualTo(1L);
        assertThat(regionA.getName()).isEqualTo("Region Amount");
        Region regionB = entityManager.find(Region.class, regionId2);
        assertThat(regionB.getCreatedAt()).isNotNull();
        assertThat(regionB.getCreatedBy()).isNotNull();
        assertThat(regionB.isArchived()).isFalse();
        assertThat(regionB.getVersion()).isEqualTo(1L);
        assertThat(regionB.getName()).isEqualTo("Region B");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(regionA);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(regionA, regionB);
      }
    });
  }

  /**
   * FIXME: This does not currently work because the interceptor cannot detect replacing whole collections.
   */
  // @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void twoEntitiesSingleCollectionWithReplacedList(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        put("regionId", regionId, "originId", originId);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        long regionId2 = masterdataService.createNewRegion("Region B");
        put("regionId2", regionId2);
        List<Region> newRegions = new ArrayList<>(o.getRegions());
        newRegions.add(entityManager.find(Region.class, regionId2));
        o.setRegions(newRegions);
        entityManager.flush();
        assertThat(o.getId()).isEqualTo(originId + 1L);
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getCreatedBy()).isNotNull();
        assertThat(o.isArchived()).isFalse();
        assertThat(o.getVersion()).isEqualTo(2L);
      }
    });
    long regionId2 = getId("regionId2");
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region regionA = entityManager.find(Region.class, regionId);
        assertThat(regionA.getCreatedAt()).isNotNull();
        assertThat(regionA.getCreatedBy()).isNotNull();
        assertThat(regionA.isArchived()).isFalse();
        assertThat(regionA.getVersion()).isEqualTo(1L);
        assertThat(regionA.getName()).isEqualTo("Region Amount");
        Region regionB = entityManager.find(Region.class, regionId2);
        assertThat(regionB.getCreatedAt()).isNotNull();
        assertThat(regionB.getCreatedBy()).isNotNull();
        assertThat(regionB.isArchived()).isFalse();
        assertThat(regionB.getVersion()).isEqualTo(1L);
        assertThat(regionB.getName()).isEqualTo("Region B");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(regionA);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(regionA, regionB);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void singleCollectionAndScalarVersionCascade(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        put("regionId", regionId, "originId", originId);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        o.setName("Origin Amount (modified)");
        long regionId2 = masterdataService.createNewRegion("Region B");
        put("regionId2", regionId2);
        o.getRegions().add(entityManager.find(Region.class, regionId2));
        entityManager.flush();
        assertThat(o.getId()).isEqualTo(originId + 1L);
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getCreatedBy()).isNotNull();
        assertThat(o.isArchived()).isFalse();
        assertThat(o.getVersion()).isEqualTo(2L);
      }
    });
    long regionId2 = getId("regionId2");
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region regionA = entityManager.find(Region.class, regionId);
        assertThat(regionA.getCreatedAt()).isNotNull();
        assertThat(regionA.getCreatedBy()).isNotNull();
        assertThat(regionA.isArchived()).isFalse();
        assertThat(regionA.getVersion()).isEqualTo(1L);
        assertThat(regionA.getName()).isEqualTo("Region Amount");
        Region regionB = entityManager.find(Region.class, regionId2);
        assertThat(regionB.getCreatedAt()).isNotNull();
        assertThat(regionB.getCreatedBy()).isNotNull();
        assertThat(regionB.isArchived()).isFalse();
        assertThat(regionB.getVersion()).isEqualTo(1L);
        assertThat(regionB.getName()).isEqualTo("Region B");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(regionA);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount (modified)");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(regionA, regionB);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void testVersionCascadeWithTwoFlushes(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin", regionId);
        Origin o = entityManager.find(Origin.class, originId);
        long regionId2 = masterdataService.createNewRegion("Region B");
        o.getRegions().add(entityManager.find(Region.class, regionId2));
        put("regionId", regionId, "originId", originId, "regionId2", regionId2);
      }
    });
    long regionId = getId("regionId");
    long regionId2 = getId("regionId2");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        Region r = o.getRegions().stream().filter(e -> e.getName().equals("Region Amount")).findFirst().get();
        o.setName("Origin (modified)");
        entityManager.flush();
        r.setName("Region Amount (modified)");
        entityManager.flush();
        assertThat(r.getId()).isEqualTo(regionId + 2L);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getCreatedBy()).isNotNull();
        assertThat(r.isArchived()).isFalse();
        assertThat(r.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(3L);
        Region oldRegionA = entityManager.find(Region.class, regionId);
        assertThat(oldRegionA.getCreatedAt()).isNotNull();
        assertThat(oldRegionA.getCreatedBy()).isNotNull();
        assertThat(oldRegionA.isArchived()).isTrue();
        assertThat(oldRegionA.getVersion()).isEqualTo(1L);
        assertThat(oldRegionA.getName()).isEqualTo("Region Amount");
        Region newRegionA = entityManager.find(Region.class, regionId + 2L);
        assertThat(newRegionA.getCreatedAt()).isNotNull();
        assertThat(newRegionA.getCreatedBy()).isNotNull();
        assertThat(newRegionA.isArchived()).isFalse();
        assertThat(newRegionA.getVersion()).isEqualTo(2L);
        assertThat(newRegionA.getName()).isEqualTo("Region Amount (modified)");
        Region regionB = entityManager.find(Region.class, regionId2);
        assertThat(regionB.getCreatedAt()).isNotNull();
        assertThat(regionB.getCreatedBy()).isNotNull();
        assertThat(regionB.isArchived()).isFalse();
        assertThat(regionB.getVersion()).isEqualTo(1L);
        assertThat(regionB.getName()).isEqualTo("Region B");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin");
        assertThat(oldOrigin.getRegions()).containsExactlyInAnyOrder(oldRegionA, regionB);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin (modified)");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(newRegionA, regionB);
      }
    });
  }

  /*
   * Modify only one entity inside of a collection containing two elements. This should result in the other unmodified
   * collection element to be reused in the collection of the new owner version.
   */
  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void changeOneInTwoElementCollectionVersionCascade(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        Origin o = entityManager.find(Origin.class, originId);
        long regionId2 = masterdataService.createNewRegion("Region B");
        o.getRegions().add(entityManager.find(Region.class, regionId2));
        put("regionId", regionId, "originId", originId, "regionId2", regionId2);
      }
    });
    long regionId = getId("regionId");
    long regionId2 = getId("regionId2");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        entityManager.flush();
        assertThat(r.getId()).isEqualTo(regionId + 2L);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getCreatedBy()).isNotNull();
        assertThat(r.isArchived()).isFalse();
        assertThat(r.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(3L);
        Region oldRegionA = entityManager.find(Region.class, regionId);
        assertThat(oldRegionA.getCreatedAt()).isNotNull();
        assertThat(oldRegionA.getCreatedBy()).isNotNull();
        assertThat(oldRegionA.isArchived()).isTrue();
        assertThat(oldRegionA.getVersion()).isEqualTo(1L);
        assertThat(oldRegionA.getName()).isEqualTo("Region Amount");
        Region newRegionA = entityManager.find(Region.class, regionId + 2L);
        assertThat(newRegionA.getCreatedAt()).isNotNull();
        assertThat(newRegionA.getCreatedBy()).isNotNull();
        assertThat(newRegionA.isArchived()).isFalse();
        assertThat(newRegionA.getVersion()).isEqualTo(2L);
        assertThat(newRegionA.getName()).isEqualTo("Region Amount (modified)");
        Region regionB = entityManager.find(Region.class, regionId2);
        assertThat(regionB.getCreatedAt()).isNotNull();
        assertThat(regionB.getCreatedBy()).isNotNull();
        assertThat(regionB.isArchived()).isFalse();
        assertThat(regionB.getVersion()).isEqualTo(1L);
        assertThat(regionB.getName()).isEqualTo("Region B");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(oldRegionA, regionB);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(newRegionA, regionB);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void changeTwoInTwoElementCollectionVersionCascade(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionIdA = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionIdA);
        Origin o = entityManager.find(Origin.class, originId);
        long regionIdB = masterdataService.createNewRegion("Region B");
        o.getRegions().add(entityManager.find(Region.class, regionIdB));
        put("regionIdA", regionIdA, "originId", originId, "regionIdB", regionIdB);
      }
    });
    long regionIdA = getId("regionIdA");
    long regionIdB = getId("regionIdB");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region rA = entityManager.find(Region.class, regionIdA);
        rA.setName("Region Amount (modified)");
        Region rB = entityManager.find(Region.class, regionIdB);
        rB.setName("Region B (modified)");
        entityManager.flush();
        assertThat(rA.getCreatedAt()).isNotNull();
        assertThat(rA.getCreatedBy()).isNotNull();
        assertThat(rA.isArchived()).isFalse();
        assertThat(rA.getVersion()).isEqualTo(2L);
        assertThat(rB.getCreatedAt()).isNotNull();
        assertThat(rB.getCreatedBy()).isNotNull();
        assertThat(rB.isArchived()).isFalse();
        assertThat(rB.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(4L);
        Region oldRegionA = entityManager.find(Region.class, regionIdA);
        assertThat(oldRegionA.getCreatedAt()).isNotNull();
        assertThat(oldRegionA.getCreatedBy()).isNotNull();
        assertThat(oldRegionA.isArchived()).isTrue();
        assertThat(oldRegionA.getVersion()).isEqualTo(1L);
        assertThat(oldRegionA.getName()).isEqualTo("Region Amount");
        Region newRegionA = entityManager
            .createQuery("from " + Region.class.getSimpleName() + " where name = :name", Region.class)
            .setParameter("name", "Region Amount (modified)").getSingleResult();
        assertThat(newRegionA.getCreatedAt()).isNotNull();
        assertThat(newRegionA.getCreatedBy()).isNotNull();
        assertThat(newRegionA.isArchived()).isFalse();
        assertThat(newRegionA.getVersion()).isEqualTo(2L);
        Region oldRegionB = entityManager.find(Region.class, regionIdB);
        assertThat(oldRegionB.getCreatedAt()).isNotNull();
        assertThat(oldRegionB.getCreatedBy()).isNotNull();
        assertThat(oldRegionB.isArchived()).isTrue();
        assertThat(oldRegionB.getVersion()).isEqualTo(1L);
        assertThat(oldRegionB.getName()).isEqualTo("Region B");
        Region newRegionB = entityManager
            .createQuery("from " + Region.class.getSimpleName() + " where name = :name", Region.class)
            .setParameter("name", "Region B (modified)").getSingleResult();
        assertThat(newRegionB.getCreatedAt()).isNotNull();
        assertThat(newRegionB.getCreatedBy()).isNotNull();
        assertThat(newRegionB.isArchived()).isFalse();
        assertThat(newRegionB.getVersion()).isEqualTo(2L);
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(oldRegionA, oldRegionB);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(newRegionA, newRegionB);
      }
    });
  }

  /*
   * When modifying an origin, we expect a new quality version but not a new version of the regions contained in the
   * origin. So, the regions must be reused.
   */
  @ParameterizedTest
  @CsvSource({ "false, false" })
  void versionCascadeOnlyToIncomingReferences(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        long qualityId = masterdataService.createNewQuality("Quality Amount", originId);
        put("regionId", regionId, "originId", originId, "qualityId", qualityId);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    long qualityId = getId("qualityId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        o.setName("Origin Amount (modified)");
        entityManager.flush();
        assertThat(o.getId()).isEqualTo(originId + 1L);
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getCreatedBy()).isNotNull();
        assertThat(o.isArchived()).isFalse();
        assertThat(o.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(1L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Quality.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region region = entityManager.find(Region.class, regionId);
        assertThat(region.getCreatedAt()).isNotNull();
        assertThat(region.getCreatedBy()).isNotNull();
        assertThat(region.isArchived()).isFalse();
        assertThat(region.getVersion()).isEqualTo(1L);
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(region);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount (modified)");
        assertThat(newOrigin.getRegions()).containsExactly(region);
        Quality newQuality = entityManager
            .createQuery("SELECT q FROM " + Quality.class.getSimpleName() + " q WHERE q.origin = :origin",
                Quality.class)
            .setParameter("origin", newOrigin).getSingleResult();
        assertThat(newQuality.getId()).isEqualTo(qualityId + 1L);
        assertThat(newQuality.getCreatedAt()).isNotNull();
        assertThat(newQuality.getCreatedBy()).isNotNull();
        assertThat(newQuality.isArchived()).isFalse();
        assertThat(newQuality.getVersion()).isEqualTo(2L);
        assertThat(newQuality.getName()).isEqualTo("Quality Amount");
        assertThat(newQuality.getOrigin().isArchived()).isFalse();
        Quality oldQuality = entityManager
            .createQuery("SELECT q FROM " + Quality.class.getSimpleName() + " q WHERE q.origin = :origin",
                Quality.class)
            .setParameter("origin", oldOrigin).getSingleResult();
        assertThat(oldQuality.getId()).isEqualTo(qualityId);
        assertThat(oldQuality.getCreatedAt()).isNotNull();
        assertThat(oldQuality.getCreatedBy()).isNotNull();
        assertThat(oldQuality.isArchived()).isTrue();
        assertThat(oldQuality.getName()).isEqualTo("Quality Amount");
        assertThat(oldQuality.getOrigin().isArchived()).isTrue();
        assertThat(oldQuality.getOrigin().getRegions()).containsExactly(region);
      }
    });
  }

  /*
   * What happens when we modify two collections of the same owner.
   */
  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void modifyTwoCollectionsOfSameOwner(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long valuationId = masterdataService.createNewValuation();
        long cropYearId = masterdataService.createNewCropYear("2018");
        long rvId = masterdataService.createNewRelativeValuation(10L);
        Valuation v = entityManager.find(Valuation.class, valuationId);
        v.getCropYears().add(entityManager.find(CropYear.class, cropYearId));
        v.getOffsets().add(entityManager.find(Relative.class, rvId));
        put("valuationId", valuationId, "cropYearIdA", cropYearId, "rvId", rvId);
      }
    });
    long valuationId = getId("valuationId");
    long cropYearIdA = getId("cropYearIdA");
    long rvId = getId("rvId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Valuation valuation = entityManager.find(Valuation.class, valuationId);
        long newCropYearId = masterdataService.createNewCropYear("2018/2019");
        long newRelativeValuationId = masterdataService.createNewRelativeValuation(20L);
        valuation.getCropYears().add(entityManager.find(CropYear.class, newCropYearId));
        valuation.getOffsets().add(entityManager.find(Relative.class, newRelativeValuationId));
        put("cropYearIdB", newCropYearId, "rvId2", newRelativeValuationId);
        entityManager.flush();
        assertThat(valuation.getId()).isEqualTo(valuationId + 1L);
        assertThat(valuation.getCreatedAt()).isNotNull();
        assertThat(valuation.getCreatedBy()).isNotNull();
        assertThat(valuation.isArchived()).isFalse();
        assertThat(valuation.getVersion()).isEqualTo(2L);
      }
    });
    long cropYearIdB = getId("cropYearIdB");
    long rvId2 = getId("rvId2");
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Valuation.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + CropYear.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Relative.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        CropYear cropYearA = entityManager.find(CropYear.class, cropYearIdA);
        assertThat(cropYearA.getCreatedAt()).isNotNull();
        assertThat(cropYearA.getCreatedBy()).isNotNull();
        assertThat(cropYearA.isArchived()).isFalse();
        assertThat(cropYearA.getVersion()).isEqualTo(1L);
        assertThat(cropYearA.getName()).isEqualTo("2018");
        CropYear cropYearB = entityManager.find(CropYear.class, cropYearIdB);
        assertThat(cropYearB.getCreatedAt()).isNotNull();
        assertThat(cropYearB.getCreatedBy()).isNotNull();
        assertThat(cropYearB.isArchived()).isFalse();
        assertThat(cropYearB.getVersion()).isEqualTo(1L);
        assertThat(cropYearB.getName()).isEqualTo("2018/2019");
        Relative relativeValuationA = entityManager.find(Relative.class, rvId);
        assertThat(relativeValuationA.getCreatedAt()).isNotNull();
        assertThat(relativeValuationA.getCreatedBy()).isNotNull();
        assertThat(relativeValuationA.isArchived()).isFalse();
        assertThat(relativeValuationA.getVersion()).isEqualTo(1L);
        Relative relativeValuationB = entityManager.find(Relative.class, rvId2);
        assertThat(relativeValuationB.getCreatedAt()).isNotNull();
        assertThat(relativeValuationB.getCreatedBy()).isNotNull();
        assertThat(relativeValuationB.isArchived()).isFalse();
        assertThat(relativeValuationB.getVersion()).isEqualTo(1L);
        Valuation oldValuation = entityManager.find(Valuation.class, valuationId);
        assertThat(oldValuation.getCreatedAt()).isNotNull();
        assertThat(oldValuation.getCreatedBy()).isNotNull();
        assertThat(oldValuation.isArchived()).isTrue();
        assertThat(oldValuation.getVersion()).isEqualTo(1L);
        assertThat(oldValuation.getCropYears()).containsExactly(cropYearA);
        assertThat(oldValuation.getOffsets()).containsExactly(relativeValuationA);
        Valuation newValuation = entityManager.find(Valuation.class, valuationId + 1L);
        assertThat(newValuation.getCreatedAt()).isNotNull();
        assertThat(newValuation.getCreatedBy()).isNotNull();
        assertThat(newValuation.isArchived()).isFalse();
        assertThat(newValuation.getVersion()).isEqualTo(2L);
        assertThat(newValuation.getCropYears()).containsExactlyInAnyOrder(cropYearA, cropYearB);
        assertThat(newValuation.getOffsets()).containsExactlyInAnyOrder(relativeValuationA, relativeValuationB);
      }
    });
  }

  /*
   * What happens when we modify two collections of the same owner.
   */
  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void modifyElementsInTwoCollectionsOfSameOwner(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long valuationId = masterdataService.createNewValuation();
        long cropYearId = masterdataService.createNewCropYear("2018");
        long rvId = masterdataService.createNewRelativeValuation(10L);
        Valuation v = entityManager.find(Valuation.class, valuationId);
        v.getCropYears().add(entityManager.find(CropYear.class, cropYearId));
        v.getOffsets().add(entityManager.find(Relative.class, rvId));
        put("valuationId", valuationId, "cropYearId", cropYearId, "rvId", rvId);
      }
    });
    long valuationId = getId("valuationId");
    long cropYearId = getId("cropYearId");
    long rvId = getId("rvId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        CropYear cropYear = entityManager.find(CropYear.class, cropYearId);
        cropYear.setName("2018 (modified)");
        Relative relativeValuation = entityManager.find(Relative.class, rvId);
        relativeValuation.setDifferentialOffset(5L);
        entityManager.flush();
        assertThat(cropYear.getId()).isEqualTo(cropYearId + 1L);
        assertThat(cropYear.getCreatedAt()).isNotNull();
        assertThat(cropYear.getCreatedBy()).isNotNull();
        assertThat(cropYear.isArchived()).isFalse();
        assertThat(cropYear.getVersion()).isEqualTo(2L);
        assertThat(relativeValuation.getId()).isEqualTo(rvId + 1L);
        assertThat(relativeValuation.getCreatedAt()).isNotNull();
        assertThat(relativeValuation.getCreatedBy()).isNotNull();
        assertThat(relativeValuation.isArchived()).isFalse();
        assertThat(relativeValuation.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(
            entityManager.createQuery("SELECT COUNT(e) FROM " + Valuation.class.getSimpleName() + " e", Long.class)
                .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + CropYear.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Relative.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        CropYear oldCropYear = entityManager.find(CropYear.class, cropYearId);
        assertThat(oldCropYear.getCreatedAt()).isNotNull();
        assertThat(oldCropYear.getCreatedBy()).isNotNull();
        assertThat(oldCropYear.isArchived()).isTrue();
        assertThat(oldCropYear.getVersion()).isEqualTo(1L);
        assertThat(oldCropYear.getName()).isEqualTo("2018");
        CropYear newCropYear = entityManager.find(CropYear.class, cropYearId + 1L);
        assertThat(newCropYear.getCreatedAt()).isNotNull();
        assertThat(newCropYear.getCreatedBy()).isNotNull();
        assertThat(newCropYear.isArchived()).isFalse();
        assertThat(newCropYear.getVersion()).isEqualTo(2L);
        assertThat(newCropYear.getName()).isEqualTo("2018 (modified)");
        Relative oldRelativeValuation = entityManager.find(Relative.class, rvId);
        assertThat(oldRelativeValuation.getCreatedAt()).isNotNull();
        assertThat(oldRelativeValuation.getCreatedBy()).isNotNull();
        assertThat(oldRelativeValuation.isArchived()).isTrue();
        assertThat(oldRelativeValuation.getVersion()).isEqualTo(1L);
        Relative newRelativeValuation = entityManager.find(Relative.class, rvId + 1L);
        assertThat(newRelativeValuation.getCreatedAt()).isNotNull();
        assertThat(newRelativeValuation.getCreatedBy()).isNotNull();
        assertThat(newRelativeValuation.isArchived()).isFalse();
        assertThat(newRelativeValuation.getVersion()).isEqualTo(2L);
        Valuation oldValuation = entityManager.find(Valuation.class, valuationId);
        assertThat(oldValuation.getCreatedAt()).isNotNull();
        assertThat(oldValuation.getCreatedBy()).isNotNull();
        assertThat(oldValuation.isArchived()).isTrue();
        assertThat(oldValuation.getVersion()).isEqualTo(1L);
        assertThat(oldValuation.getCropYears()).containsExactly(oldCropYear);
        assertThat(oldValuation.getOffsets()).containsExactly(oldRelativeValuation);
        Valuation newValuation = entityManager.find(Valuation.class, valuationId + 1L);
        assertThat(newValuation.getCreatedAt()).isNotNull();
        assertThat(newValuation.getCreatedBy()).isNotNull();
        assertThat(newValuation.isArchived()).isFalse();
        assertThat(newValuation.getVersion()).isEqualTo(2L);
        assertThat(newValuation.getCropYears()).containsExactly(newCropYear);
        assertThat(newValuation.getOffsets()).containsExactly(newRelativeValuation);
      }
    });
  }

  /*
   * Basically, this test was added to assert that a new version of a collection owner does not lead to the old owner
   * losing its collection entries.
   */
  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void reuseNonModifiedCollectionWithTwoElements(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        Origin o = entityManager.find(Origin.class, originId);
        long regionId2 = masterdataService.createNewRegion("Region B");
        o.getRegions().add(entityManager.find(Region.class, regionId2));
        put("regionId", regionId, "originId", originId, "regionId2", regionId2);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    long regionId2 = getId("regionId2");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        o.setName("Origin Amount (modified)");
        entityManager.flush();
        assertThat(o.getId()).isEqualTo(originId + 1L);
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getCreatedBy()).isNotNull();
        assertThat(o.isArchived()).isFalse();
        assertThat(o.getVersion()).isEqualTo(2L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region regionA = entityManager.find(Region.class, regionId);
        assertThat(regionA.getCreatedAt()).isNotNull();
        assertThat(regionA.getCreatedBy()).isNotNull();
        assertThat(regionA.isArchived()).isFalse();
        assertThat(regionA.getVersion()).isEqualTo(1L);
        assertThat(regionA.getName()).isEqualTo("Region Amount");
        Region regionB = entityManager.find(Region.class, regionId2);
        assertThat(regionB.getCreatedAt()).isNotNull();
        assertThat(regionB.getCreatedBy()).isNotNull();
        assertThat(regionB.isArchived()).isFalse();
        assertThat(regionB.getVersion()).isEqualTo(1L);
        assertThat(regionB.getName()).isEqualTo("Region B");
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactlyInAnyOrder(regionA, regionB);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount (modified)");
        assertThat(newOrigin.getRegions()).containsExactlyInAnyOrder(regionA, regionB);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false", "true" })
  void selectOnModifiedPropertyValueWillNotFindEntity(boolean shouldClear) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        put("regionId", regionId);
      }
    });
    long regionId = getId("regionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification and assertion in same transaction */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        // cannot find objects using properties that changed in the current
        // transaction!
        assertThrows(NoResultException.class,
            () -> entityManager
                .createQuery("FROM " + Region.class.getSimpleName() + " WHERE name = :name", Region.class)
                .setParameter("name", "Region Amount (modified)").getSingleResult());
        // Query on id will find modified object
        r = entityManager.createQuery("FROM " + Region.class.getSimpleName() + " WHERE id = :id", Region.class)
            .setParameter("id", regionId).getSingleResult();
        assertThat(r.getName()).isEqualTo("Region Amount (modified)");
        // EntityManager.find() will find modified object
        r = entityManager.find(Region.class, regionId);
        assertThat(r.getName()).isEqualTo("Region Amount (modified)");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void selectShouldNotFlushWhenFlushOnCommit(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        put("regionId", regionId, "originId", originId);
      }
    });
    long regionId = getId("regionId");
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        /*
         * The following select should NOT flush and should return the old version of the origin
         */
        Origin o = entityManager.createQuery("from " + Origin.class.getSimpleName() + " where id = :id", Origin.class)
            .setParameter("id", originId).getSingleResult();
        o.setName("Origin Amount (modified)");
        entityManager.flush();
        /* Now, the changes should have been made */
        assertThat(r.getId()).isEqualTo(regionId + 1L);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        /* Assertion */
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Origin.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery("SELECT COUNT(e) FROM " + Region.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(2L);
        Region oldRegion = entityManager.find(Region.class, regionId);
        assertThat(oldRegion.getCreatedAt()).isNotNull();
        assertThat(oldRegion.getCreatedBy()).isNotNull();
        assertThat(oldRegion.isArchived()).isTrue();
        assertThat(oldRegion.getVersion()).isEqualTo(1L);
        Region newRegion = entityManager.find(Region.class, regionId + 1L);
        assertThat(newRegion.getCreatedAt()).isNotNull();
        assertThat(newRegion.getCreatedBy()).isNotNull();
        assertThat(newRegion.isArchived()).isFalse();
        assertThat(newRegion.getVersion()).isEqualTo(2L);
        Origin oldOrigin = entityManager.find(Origin.class, originId);
        assertThat(oldOrigin.getCreatedAt()).isNotNull();
        assertThat(oldOrigin.getCreatedBy()).isNotNull();
        assertThat(oldOrigin.isArchived()).isTrue();
        assertThat(oldOrigin.getVersion()).isEqualTo(1L);
        assertThat(oldOrigin.getName()).isEqualTo("Origin Amount");
        assertThat(oldOrigin.getRegions()).containsExactly(oldRegion);
        Origin newOrigin = entityManager.find(Origin.class, originId + 1L);
        assertThat(newOrigin.getCreatedAt()).isNotNull();
        assertThat(newOrigin.getCreatedBy()).isNotNull();
        assertThat(newOrigin.isArchived()).isFalse();
        assertThat(newOrigin.getVersion()).isEqualTo(2L);
        assertThat(newOrigin.getName()).isEqualTo("Origin Amount (modified)");
        assertThat(newOrigin.getRegions()).containsExactly(newRegion);
      }
    });
  }

  /*
   * Interceptor.preFlush() is always called with all entities contained in the first-level cache. So, loading
   * old/archived entities and performing no modifications on them MUST NOT result in an exception when flushing.
   */
  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void flushIsCalledWithUnmodifiedEntitiesInThePersistenceContext(boolean shouldClear,
      boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        put("regionId", regionId);
      }
    });
    long regionId = getId("regionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification and Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
        entityManager.flush();
        if (shouldClear)
          entityManager.clear();
        r = entityManager.find(Region.class, regionId);
        entityManager.flush();
        if (shouldClearForAssertion)
          entityManager.clear();
        assertThat(r.getId()).isEqualTo(regionId);
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void scalarModificationOnArchivedEntityShouldThrow(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        put("regionId", regionId);
      }
    });
    long regionId = getId("regionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified)");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Another modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Region r = entityManager.find(Region.class, regionId);
        r.setName("Region Amount (modified again)");
        /* Assertion */
        assertThrows(ModifiedArchivedException.class, () -> entityManager.flush());
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void collectionModificationOnArchivedEntityShouldThrow(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        long originId = masterdataService.createNewOrigin("Origin Amount", regionId);
        put("originId", originId);
      }
    });
    long originId = getId("originId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Origin o = entityManager.find(Origin.class, originId);
        o.getRegions().clear();
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Another modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        Origin o = entityManager.find(Origin.class, originId);
        o.getRegions().add(entityManager.find(Region.class, masterdataService.createNewRegion("Region B")));
        /* Assertion */
        assertThrows(ModifiedArchivedException.class, () -> entityManager.flush());
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void oneUnversionedEntityScalarChange(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long contractVersionId = masterdataService.createNewContract("P1");
        put("contractVersionId", contractVersionId);
      }
    });
    long contractVersionId = getId("contractVersionId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract cv = entityManager.find(Contract.class, contractVersionId);
        ContractIdentity c = cv.getIdentity();
        c.setUnversionedProperty("Hello");
        entityManager.flush();
        assertThat(cv.getId()).isEqualTo(contractVersionId);
        assertThat(cv.getCreatedAt()).isNotNull();
        assertThat(cv.getCreatedBy()).isNotNull();
        assertThat(cv.isArchived()).isFalse();
        assertThat(cv.getVersion()).isEqualTo(1L);
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getCreatedBy()).isNotNull();
        assertThat(c.getUnversionedProperty()).isEqualTo("Hello");
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + ContractIdentity.class.getSimpleName() + " e", Long.class)
            .getSingleResult()).isEqualTo(1L);
        Contract contractVersion = entityManager.find(Contract.class, contractVersionId);
        assertThat(contractVersion.getCreatedAt()).isNotNull();
        assertThat(contractVersion.getCreatedBy()).isNotNull();
        assertThat(contractVersion.isArchived()).isFalse();
        assertThat(contractVersion.getVersion()).isEqualTo(1L);
        assertThat(contractVersion.getKey()).isEqualTo("P1");
        ContractIdentity contract = contractVersion.getIdentity();
        assertThat(contract.getCreatedAt()).isNotNull();
        assertThat(contract.getCreatedBy()).isNotNull();
        assertThat(contract.getUnversionedProperty()).isEqualTo("Hello");
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void collectionMarkRemovedCompositeCollectionElementDeleted(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = new Contract();
        c.setKey("S1");
        Shipment shipment = new Shipment();
        c.getShipments().add(shipment);
        entityManager.persist(c);
        put("contractId", c.getId(), "shipmentId", c.getShipments().get(0).getId());
      }
    });
    long contractId = getId("contractId");
    long shipmentId = getId("shipmentId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Contract c = entityManager.find(Contract.class, contractId);
        /* Remove the shipment (which will be marked deleted=true afterwards) */
        c.getShipments().remove(0);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();
    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        /* There are two contracts */
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Contract.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        /* And two shipments... */
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Shipment.class.getSimpleName() + " e", Long.class).getSingleResult())
                .isEqualTo(2L);
        /* ...one of which is deleted */
        assertThat(entityManager
            .createQuery("SELECT COUNT(e) FROM " + Shipment.class.getSimpleName() + " e WHERE deleted = true",
                Long.class)
            .getSingleResult()).isEqualTo(1L);
        Shipment oldShipment = entityManager.find(Shipment.class, shipmentId);
        Contract oldContract = entityManager.find(Contract.class, contractId);
        Contract newContract = entityManager.find(Contract.class, contractId + 1L);
        assertThat(oldContract.getShipments()).containsExactly(oldShipment);
        assertThat(newContract.getShipments()).isEmpty();
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void collectionModificationOnUnversionedEntity(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long uqualityId = masterdataService.createNewUQuality();
        long valuationId = masterdataService.createNewValuation();
        entityManager.find(QualityIdentity.class, uqualityId).getValuations()
            .add(entityManager.find(Valuation.class, valuationId));
        put("uqualityId", uqualityId, "valuationId", valuationId);
      }
    });
    long uqualityId = getId("uqualityId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        QualityIdentity q = entityManager.find(QualityIdentity.class, uqualityId);
        q.getValuations().clear();
      }
    });
    if (shouldClear)
      entityManager.clear();

    /* Another modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        QualityIdentity q = entityManager.find(QualityIdentity.class, uqualityId);
        long newValuationId = masterdataService.createNewValuation();
        q.getValuations().add(entityManager.find(Valuation.class, newValuationId));
        put("newValuationId", newValuationId);
      }
    });
    long newValuationId = getId("newValuationId");
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        QualityIdentity q = entityManager.find(QualityIdentity.class, uqualityId);
        assertThat(q.getValuations()).containsExactly(entityManager.find(Valuation.class, newValuationId));
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void collectionElementModifiedInUnversionedEntity(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long uqualityId = masterdataService.createNewUQuality();
        long valuationId = masterdataService.createNewValuation();
        entityManager.find(QualityIdentity.class, uqualityId).getValuations()
            .add(entityManager.find(Valuation.class, valuationId));
        put("uqualityId", uqualityId, "valuationId", valuationId);
      }
    });
    long uqualityId = getId("uqualityId");
    long valuationId = getId("valuationId");
    if (shouldClear)
      entityManager.clear();

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        Valuation valuation = entityManager.find(Valuation.class, valuationId);
        valuation.setValidFrom(new Date(1000L));
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        QualityIdentity q = entityManager.find(QualityIdentity.class, uqualityId);
        assertThat(q.getValuations()).containsExactly(entityManager.find(Valuation.class, valuationId + 1L));
      }
    });
  }

  @Test
  void concurrentModificationsOnEntity() throws InterruptedException {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long regionId = masterdataService.createNewRegion("Region Amount");
        put("regionId", regionId);
      }
    });
    long regionId = getId("regionId");

    /* Modification */
    AtomicReference<Throwable> throwable1 = new AtomicReference<Throwable>(null);
    CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
    CountDownLatch latch = new CountDownLatch(1);
    Thread t1 = new Thread(new Runnable() {
      public void run() {
        try {
          transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
              Region r = entityManager.find(Region.class, regionId);
              r.setName("Region Amount (modified 1)");
              try {
                cyclicBarrier.await();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          });
        } catch (Exception e) {
          throwable1.set(e);
        }
      }
    });
    AtomicReference<Throwable> throwable2 = new AtomicReference<Throwable>(null);
    Thread t2 = new Thread(new Runnable() {
      public void run() {
        try {
          transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
              Region r = entityManager.find(Region.class, regionId);
              r.setName("Region Amount (modified 2)");
              try {
                cyclicBarrier.await();
                latch.await();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          });
        } catch (Exception e) {
          throwable2.set(e);
        }
      }
    });
    t1.start();
    t2.start();
    t1.join();
    latch.countDown();
    t2.join();
    assertNull(throwable1.get());
    assertNotNull(throwable2.get());
    Region r = entityManager.find(Region.class, regionId + 1L);
    assertThat(r.getName()).isEqualTo("Region Amount (modified 1)");
  }

  /**
   * Test the sumQualityAmount field of {@link Shipment}, which is an Embeddable. When modifying the amount value of
   * that embeddable, we expect a new version of the {@link Shipment} and the {@link Contract} to be created.
   */
  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void testEmbeddable(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        put("contractId", contractService.createNewContractWithShipments("P1"));
      }
    });
    long contractId = (Long) ids.get("contractId");
    if (shouldClear)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        assertThat(entityManager
            .createQuery("select count(e) from " + Contract.class.getSimpleName() + " e where archived = false")
            .getSingleResult()).isEqualTo(1L);
        assertThat(entityManager
            .createQuery("select count(e) from " + Shipment.class.getSimpleName() + " e where archived = false")
            .getSingleResult()).isEqualTo(2L);
      }
    });

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        contractService.updateSumQualityAmountOfFirstShipment(contractId, 2.0);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery(
                "select count(e) from " + Contract.class.getSimpleName() + " e where archived = true and id = :id")
            .setParameter("id", contractId).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager
            .createQuery(
                "select count(e) from " + Contract.class.getSimpleName() + " e where archived = false and id = :id")
            .setParameter("id", contractId + 1L).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager.createQuery("select count(e) from " + Shipment.class.getSimpleName() + " e")
            .getSingleResult()).isEqualTo(3L);
        /* Assert that their shipments have the expected amounts */
        Contract e0 = entityManager.find(Contract.class, contractId);
        boolean oneFound = false, twoFound = false;
        for (Shipment s : e0.getShipments()) {
          oneFound |= s.getAmount() == 1 && s.getVersion() == 1L
              && s.getSumQualityAmount().getValue().compareTo(BigDecimal.TEN) == 0;
          twoFound |= s.getAmount() == 2 && s.getVersion() == 1L
              && s.getSumQualityAmount().getValue().compareTo(BigDecimal.ONE) == 0;
          assertThat(s.isArchived()).isEqualTo(s.getAmount() == 1);
        }
        assertThat(oneFound && twoFound).isTrue();
        Contract e1 = entityManager.find(Contract.class, contractId + 1);
        oneFound = false;
        twoFound = false;
        for (Shipment s : e1.getShipments()) {
          oneFound |= s.getAmount() == 1 && s.getVersion() == 2L
              && s.getSumQualityAmount().getValue().compareTo(new BigDecimal(2.0)) == 0;
          twoFound |= s.getAmount() == 2 && s.getVersion() == 1L
              && s.getSumQualityAmount().getValue().compareTo(BigDecimal.ONE) == 0;
          assertThat(s.isArchived()).isFalse();
        }
        assertThat(oneFound && twoFound).isTrue();
      }
    });
  }

  @ParameterizedTest
  @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
  void testEmptyEmbeddable(boolean shouldClear, boolean shouldClearForAssertion) {
    /* Initial test data */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        long contractId = contractService.createNewContractWithShipmentsAndEmptySumQualityAmount("P1");
        put("contractId", contractId);
      }
    });
    long contractId = (Long) ids.get("contractId");
    if (shouldClear)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        assertThat(entityManager
            .createQuery("select count(e) from " + Contract.class.getSimpleName() + " e where archived = false")
            .getSingleResult()).isEqualTo(1L);
        assertThat(entityManager
            .createQuery("select count(e) from " + Shipment.class.getSimpleName() + " e where archived = false")
            .getSingleResult()).isEqualTo(2L);
      }
    });

    /* Modification */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        contractService.setSumQualityAmountOfFirstShipment(contractId, 2.0);
      }
    });
    if (shouldClearForAssertion)
      entityManager.clear();

    /* Assertion */
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        status.setRollbackOnly();
        assertThat(entityManager
            .createQuery(
                "select count(e) from " + Contract.class.getSimpleName() + " e where archived = true and id = :id")
            .setParameter("id", contractId).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager
            .createQuery(
                "select count(e) from " + Contract.class.getSimpleName() + " e where archived = false and id = :id")
            .setParameter("id", contractId + 1L).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager.createQuery("select count(e) from " + Shipment.class.getSimpleName() + " e")
            .getSingleResult()).isEqualTo(3L);
        /* Assert that their shipments have the expected amounts */
        Contract e0 = entityManager.find(Contract.class, contractId);
        boolean oneFound = false, twoFound = false;
        for (Shipment s : e0.getShipments()) {
          oneFound |= s.getAmount() == 1 && s.getVersion() == 1L && s.getSumQualityAmount() == null;
          twoFound |= s.getAmount() == 2 && s.getVersion() == 1L && s.getSumQualityAmount() == null;
          assertThat(s.isArchived()).isEqualTo(s.getAmount() == 1);
        }
        assertThat(oneFound && twoFound).isTrue();
        Contract e1 = entityManager.find(Contract.class, contractId + 1);
        oneFound = false;
        twoFound = false;
        for (Shipment s : e1.getShipments()) {
          oneFound |= s.getAmount() == 1 && s.getVersion() == 2L
              && s.getSumQualityAmount().getValue().compareTo(BigDecimal.valueOf(2.0)) == 0;
          twoFound |= s.getAmount() == 2 && s.getVersion() == 1L && s.getSumQualityAmount() == null;
          assertThat(s.isArchived()).isFalse();
        }
        assertThat(oneFound && twoFound).isTrue();
      }
    });
  }
}
