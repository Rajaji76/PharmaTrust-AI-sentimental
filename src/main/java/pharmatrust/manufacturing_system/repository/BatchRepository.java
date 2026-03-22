package pharmatrust.manufacturing_system.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.Batch;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Batch entity operations
 * Supports batch management, Merkle root updates, and expiry tracking
 */
@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    /**
     * Find batch by batch number
     */
    Optional<Batch> findByBatchNumber(String batchNumber);

    /**
     * Check if batch number exists
     */
    boolean existsByBatchNumber(String batchNumber);

    /**
     * Find all batches by manufacturer
     */
    Page<Batch> findByManufacturerId(UUID manufacturerId, Pageable pageable);
    
    /**
     * Find all batches by manufacturer (without pagination)
     */
    List<Batch> findByManufacturer(pharmatrust.manufacturing_system.entity.User manufacturer);

    /**
     * Find batches by status
     */
    List<Batch> findByStatus(Batch.BatchStatus status);

    /**
     * Find batches by manufacturer and status
     */
    Page<Batch> findByManufacturerIdAndStatus(UUID manufacturerId, Batch.BatchStatus status, Pageable pageable);

    /**
     * Find batches expiring soon (within 30 days) that haven't been alerted
     */
    @Query("SELECT b FROM Batch b WHERE b.expiryDate <= :warningDate AND b.expiryAlertSent = false AND b.status = 'ACTIVE'")
    List<Batch> findBatchesExpiringSoon(@Param("warningDate") LocalDate warningDate);

    /**
     * Find expired batches
     */
    @Query("SELECT b FROM Batch b WHERE b.expiryDate < :currentDate AND b.status = 'ACTIVE'")
    List<Batch> findExpiredBatches(@Param("currentDate") LocalDate currentDate);

    /**
     * Update Merkle root for batch (idempotent)
     */
    @Modifying
    @Query("UPDATE Batch b SET b.merkleRoot = :merkleRoot WHERE b.id = :batchId")
    void updateMerkleRoot(@Param("batchId") UUID batchId, @Param("merkleRoot") String merkleRoot);

    /**
     * Update blockchain transaction ID
     */
    @Modifying
    @Query("UPDATE Batch b SET b.blockchainTxId = :txId WHERE b.id = :batchId")
    void updateBlockchainTxId(@Param("batchId") UUID batchId, @Param("txId") String txId);

    /**
     * Update batch status
     */
    @Modifying
    @Query("UPDATE Batch b SET b.status = :status WHERE b.id = :batchId")
    void updateStatus(@Param("batchId") UUID batchId, @Param("status") Batch.BatchStatus status);

    /**
     * Mark expiry alert as sent
     */
    @Modifying
    @Query("UPDATE Batch b SET b.expiryAlertSent = true WHERE b.id = :batchId")
    void markExpiryAlertSent(@Param("batchId") UUID batchId);

    /**
     * Find batches pending approval
     */
    @Query("SELECT b FROM Batch b WHERE b.status = 'PENDING_APPROVAL' ORDER BY b.createdAt DESC")
    List<Batch> findPendingApprovalBatches();

    /**
     * Find batches that have a blockchain transaction ID but are not yet confirmed.
     * Used by {@link pharmatrust.manufacturing_system.service.BlockchainTransactionMonitor}
     * to poll pending on-chain transactions.
     * Requirements: IR-004
     */
    @Query("SELECT b FROM Batch b WHERE b.blockchainTxId IS NOT NULL AND b.blockchainConfirmed = false")
    List<Batch> findBatchesWithPendingBlockchainTx();

    /**
     * Count batches by manufacturer and status
     */
    long countByManufacturerIdAndStatus(UUID manufacturerId, Batch.BatchStatus status);
}
