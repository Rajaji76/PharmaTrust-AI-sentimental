package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.OwnershipLog;

import java.util.List;
import java.util.UUID;

/**
 * Repository for OwnershipLog entity operations
 * Supports ownership transfer tracking and supply chain traceability
 */
@Repository
public interface OwnershipLogRepository extends JpaRepository<OwnershipLog, UUID> {

    /**
     * Find ownership history for a unit (chronological order)
     */
    List<OwnershipLog> findByUnitIdOrderByTransferredAtAsc(UUID unitId);

    /**
     * Find transfers from a user
     */
    List<OwnershipLog> findByFromUserIdOrderByTransferredAtDesc(UUID fromUserId);

    /**
     * Find transfers to a user
     */
    List<OwnershipLog> findByToUserIdOrderByTransferredAtDesc(UUID toUserId);

    /**
     * Find transfers by type
     */
    List<OwnershipLog> findByTransferTypeOrderByTransferredAtDesc(OwnershipLog.TransferType transferType);

    /**
     * Find pending transfers (for acceptance workflow)
     */
    @Query("SELECT ol FROM OwnershipLog ol WHERE ol.toUser.id = :userId ORDER BY ol.transferredAt DESC")
    List<OwnershipLog> findPendingTransfersForUser(@Param("userId") UUID userId);

    /**
     * Get complete supply chain for a unit
     */
    @Query("SELECT ol FROM OwnershipLog ol WHERE ol.unit.id = :unitId ORDER BY ol.transferredAt ASC")
    List<OwnershipLog> getSupplyChainTimeline(@Param("unitId") UUID unitId);

    /**
     * Count transfers for a unit
     */
    long countByUnitId(UUID unitId);
}
