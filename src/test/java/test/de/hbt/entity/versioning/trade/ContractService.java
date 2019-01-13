package test.de.hbt.entity.versioning.trade;

import java.math.*;

import javax.persistence.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import test.de.hbt.entity.versioning.masterdata.entity.*;
import test.de.hbt.entity.versioning.trade.entity.*;

@Transactional
@Service
public class ContractService {

  @Autowired
  private EntityManager entityManager;

  public long createNewContractWithShipments(String key) {
    Shipment s1 = new Shipment();
    Shipment s2 = new Shipment();
    WeightUnit wu = new WeightUnit();
    wu.setAbbreviation("kg");
    wu.setName("kg");
    wu.setKgConversion(BigDecimal.ONE);
    wu.setGlobal(true);
    entityManager.persist(wu);
    s1.setSumQualityAmount(new Amount(BigDecimal.TEN, wu));
    s1.setAmount(1);
    s2.setSumQualityAmount(new Amount(BigDecimal.ONE, wu));
    s2.setAmount(2);
    Contract e = new Contract();
    ContractIdentity c = new ContractIdentity();
    e.setIdentity(c);
    e.setKey(key);
    e.getShipments().add(s1);
    e.getShipments().add(s2);
    entityManager.persist(e);
    return e.getId();
  }

  public long createNewContractWithShipmentsAndEmptySumQualityAmount(String key) {
    Shipment s1 = new Shipment();
    Shipment s2 = new Shipment();
    WeightUnit wu = new WeightUnit();
    wu.setAbbreviation("kg");
    wu.setName("kg");
    wu.setKgConversion(BigDecimal.ONE);
    wu.setGlobal(true);
    entityManager.persist(wu);
    s1.setAmount(1);
    s2.setAmount(2);
    Contract e = new Contract();
    ContractIdentity c = new ContractIdentity();
    e.setIdentity(c);
    e.setKey(key);
    e.getShipments().add(s1);
    e.getShipments().add(s2);
    entityManager.persist(e);
    return e.getId();
  }

  public void updateSumQualityAmountOfFirstShipment(long contractId, double amount) {
    Contract ce = entityManager.find(Contract.class, contractId);
    ce.getShipments().get(0).getSumQualityAmount().setValue(new BigDecimal(amount));
  }

  public void setSumQualityAmountOfFirstShipment(long contractId, double amount) {
    Contract ce = entityManager.find(Contract.class, contractId);
    ce.getShipments().get(0)
        .setSumQualityAmount(new Amount(new BigDecimal(amount), entityManager.find(WeightUnit.class, 1L)));
  }

  public long updateFirstShipmentAndReturnNewContractVersion(long contractId, int amount) {
    Contract ce = entityManager.find(Contract.class, contractId);
    for (Shipment se : ce.getShipments()) {
      if (se.getAmount() == 1)
        se.setAmount(amount);
    }
    Contract latestVersion = (Contract) entityManager
        .createQuery("from ContractEntity where key = :key and archived = false").setParameter("key", ce.getKey())
        .getSingleResult();
    return latestVersion.getId();
  }

  public void addShipment(long contractId, int amount) {
    Contract e = entityManager.find(Contract.class, contractId);
    Shipment se = new Shipment();
    se.setAmount(amount);
    e.getShipments().add(se);
  }

  public void removeFirstShipment(long contractId) {
    Contract e = entityManager.find(Contract.class, contractId);
    e.getShipments().remove(0);
  }

  public void clearShipments(long contractId) {
    Contract e = entityManager.find(Contract.class, contractId);
    e.getShipments().clear();
  }
}
