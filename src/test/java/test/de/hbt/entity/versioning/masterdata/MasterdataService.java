package test.de.hbt.entity.versioning.masterdata;

import java.util.*;

import javax.persistence.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import test.de.hbt.entity.versioning.masterdata.entity.*;
import test.de.hbt.entity.versioning.trade.entity.*;

@Transactional
@Service
public class MasterdataService {

  @Autowired
  private EntityManager entityManager;

  public long createNewRegion(String name) {
    Region e = new Region();
    e.setName(name);
    entityManager.persist(e);
    return e.getId();
  }

  public long createNewValuation() {
    Valuation val = new Valuation();
    val.setValidFrom(new Date(500L));
    entityManager.persist(val);
    return val.getId();
  }

  public long createNewCropYear(String name) {
    CropYear cropYear = new CropYear();
    cropYear.setName(name);
    entityManager.persist(cropYear);
    return cropYear.getId();
  }

  public long createNewOrigin(String name, long regionId) {
    Origin e = new Origin();
    Region region = entityManager.find(Region.class, regionId);
    e.setRegions(new ArrayList<>(Arrays.asList(region)));
    e.setName(name);
    entityManager.persist(e);
    return e.getId();
  }

  public long createNewUQuality() {
    QualityIdentity e = new QualityIdentity();
    entityManager.persist(e);
    return e.getId();
  }

  public long createNewQuality(String name, long originId) {
    QualityIdentity ue = new QualityIdentity();
    Quality e = new Quality();
    e.setIdentity(ue);
    e.setName(name);
    e.setOrigin(entityManager.find(Origin.class, originId));
    entityManager.persist(e);
    return e.getId();
  }

  public long createNewCertification(String name) {
    Certification e = new Certification();
    e.setName(name);
    entityManager.persist(e);
    return e.getId();
  }

  public long createNewRelativeValuation(long offset) {
    Relative e = new Relative();
    e.setDifferentialOffset(offset);
    entityManager.persist(e);
    return e.getId();
  }

  public void updateRegionAndOriginAndQuality(long regionId, long originId, long qualityId, String regionName,
      String originName, String qualityName) {
    Region e = entityManager.find(Region.class, regionId);
    e.setName(regionName);
    Origin oe = entityManager.find(Origin.class, originId);
    oe.setName(originName);
    Quality qe = entityManager.find(Quality.class, qualityId);
    qe.setName(qualityName);
  }

  public long createNewContract(String key) {
    Contract contractVersion = new Contract();
    contractVersion.setKey(key);
    entityManager.persist(contractVersion);
    return contractVersion.getId();
  }

  public long createNewAllocation(Contract salesContract, Contract purchaseContract) {
    Allocation allocation = new Allocation();
    allocation.setSalesContract(salesContract);
    allocation.setPurchaseContract(purchaseContract);
    entityManager.persist(allocation);
    return allocation.getId();
  }
}
