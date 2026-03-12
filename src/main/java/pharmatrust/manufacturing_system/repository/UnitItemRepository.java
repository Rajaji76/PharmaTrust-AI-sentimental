package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.model.UnitItem;
import java.util.Optional;
import java.util.List;

@Repository
public interface UnitItemRepository extends JpaRepository<UnitItem, String> {
    Optional<UnitItem> findBySerialNumber(String serialNumber);
    
    // Service और Controller के लिए ये दोनों जोड़ें:
    List<UnitItem> findByBatch_BatchNumber(String batchNumber);
    List<UnitItem> findByParentBoxId(String parentBoxId);
}



