package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.BatchApproval;

import java.util.List;
import java.util.UUID;

/**
 * Repository for BatchApproval entity operations
 * Supports multi-signature approval workflow
 */
@Repository
public interface BatchApprovalRepository extends JpaRepository<BatchApproval, UUID> {

    /**
     * Find all approvals for a batch
     */
    List<BatchApproval> findByBatchId(UUID batchId);

    /**
     * Find approvals by batch and approval type
     */
    List<BatchApproval> findByBatchIdAndApprovalType(UUID batchId, BatchApproval.ApprovalType approvalType);

    /**
     * Check if batch has specific approval type
     */
    boolean existsByBatchIdAndApprovalType(UUID batchId, BatchApproval.ApprovalType approvalType);

    /**
     * Count approvals for a batch
     */
    long countByBatchId(UUID batchId);

    /**
     * Find approvals by approver
     */
    List<BatchApproval> findByApproverId(UUID approverId);

    /**
     * Get distinct approval types for a batch
     */
    @Query("SELECT DISTINCT ba.approvalType FROM BatchApproval ba WHERE ba.batch.id = :batchId")
    List<BatchApproval.ApprovalType> findDistinctApprovalTypesByBatchId(@Param("batchId") UUID batchId);
}
