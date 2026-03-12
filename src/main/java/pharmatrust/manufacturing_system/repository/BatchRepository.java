package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pharmatrust.manufacturing_system.model.Batch;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    Batch findByBatchNumber(String batchNumber);
}
