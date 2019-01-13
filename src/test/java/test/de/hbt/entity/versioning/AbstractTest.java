package test.de.hbt.entity.versioning;

import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import java.util.function.*;

import javax.persistence.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import de.hbt.entity.versioning.*;

public abstract class AbstractTest {

  @Autowired
  protected TransactionTemplate transactionTemplate;

  @Autowired
  protected EntityManager entityManager;

  @MockBean
  protected Supplier<Instant> nowSupplier;

  @MockBean
  protected Supplier<Principal> userSupplier;

  /** Holds all generated ids. */
  protected Map<String, Object> ids = new HashMap<>();

  private void truncateAll() {
    Random rnd = new Random();
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        entityManager.createNativeQuery("set referential_integrity false").executeUpdate();
        List<?> sequenceNames = entityManager
            .createNativeQuery(
                "select sequence_name from information_schema.sequences where sequence_schema = 'PUBLIC'")
            .getResultList();
        for (Object name : sequenceNames) {
          int startValue = 1 + rnd.nextInt(1000);
          entityManager
              .createNativeQuery("alter sequence " + ((String) name).toLowerCase() + " restart with " + startValue)
              .executeUpdate();
        }
        /* Truncate tables */
        List<?> tableNames = entityManager
            .createNativeQuery("select table_name from information_schema.tables where table_schema = 'PUBLIC'")
            .getResultList();
        for (Object name : tableNames) {
          entityManager.createNativeQuery("truncate table " + ((String) name).toLowerCase()).executeUpdate();
        }
        entityManager.createNativeQuery("set referential_integrity true").executeUpdate();
      }
    });
  }

  @BeforeEach
  protected void beforeEach() {
    when(nowSupplier.get()).thenReturn(Instant.now());
    when(userSupplier.get()).thenReturn(() -> "testuser");
  }

  @AfterEach
  protected void afterEach() {
    truncateAll();
    ids.clear();
    entityManager.clear();
  }

  protected long getId(String name) {
    return (Long) ids.get(name);
  }

  protected void put(Object... keysAndValues) {
    for (int i = 0; i < keysAndValues.length; i += 2)
      ids.put((String) keysAndValues[i], keysAndValues[i + 1]);
  }
}
