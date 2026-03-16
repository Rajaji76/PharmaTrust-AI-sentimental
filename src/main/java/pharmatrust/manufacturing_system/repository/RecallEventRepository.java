package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.RecallEvent;

import java.util.List;
import java.util.UUID;

/**
 * Repository for RecallEvent entity operations
 * Supports recall tracking and regulator dashboard
 */
@Repository
public interface RecallEventRepository extends JpaRepository<RecallEvent, UUID> {

    /**
     * Find recalls by batch
     */
    List<RecallEvent> findByBatchIdOrderByInitiatedAtDesc(UUID batchId);

    /**
     * Find recalls by initiator
     */
    List<RecallEvent> findByInitiatedByIdOrderByInitiatedAtDesc(UUID userId);

    /**
     * Find recalls by type
     */
    List<RecallEvent> findByRecallTypeOrderByInitiatedAtDesc(RecallEvent.RecallType recallType);

    /**
     * Find active recalls
     */
    List<RecallEvent> findByStatusOrderByInitiatedAtDesc(RecallEvent.RecallStatus status);

    /**
     * Find all auto-triggered recalls (for regulator dashboard)
     */
    @Query("SELECT r FROM RecallEvent r WHERE r.recallType = 'AUTO' ORDER BY r.initiatedAt DESC")
    List<RecallEvent> findAutoRecalls();

    /**
     * Find emergency recalls
     */
    @Query("SELECT r FROM RecallEvent r WHERE r.recallType = 'EMERGENCY' ORDER BY r.initiatedAt DESC")
    List<RecallEvent> findEmergencyRecalls();

    /**
     * Check if batch has active recall
     */
    boolean existsByBatchIdAndStatus(UUID batchId, RecallEvent.RecallStatus status);
}
