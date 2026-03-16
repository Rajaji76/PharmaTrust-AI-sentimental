package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.UnitItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UnitItem entity operations
 * Supports idempotent inserts, Merkle tree calculation, and scan tracking
 */
@Repository
public interface UnitItemRepository extends JpaRepository<UnitItem, UUID> {

    /**
     * Find unit by serial number
     */
    Optional<UnitItem> findBySerialNumber(String serialNumber);

    /**
     * Check if serial number exists
     */
    boolean existsBySerialNumber(String serialNumber);

    /**
     * Find all units in a batch
     */
    List<UnitItem> findByBatchId(UUID batchId);

    /**
     * Find child units by parent unit ID
     */
    List<UnitItem> findByParentUnitId(UUID parentUnitId);
    
    /**
     * Find child units by parent unit
     */
    List<UnitItem> findByParentUnit(UnitItem parentUnit);
    
    /**
     * Count children of a parent
     */
    long countByParentUnitId(UUID parentUnitId);
    
    /**
     * Check if unit has children
     */
    boolean existsByParentUnitId(UUID parentUnitId);

    /**
     * Find units by current owner
     */
    List<UnitItem> findByCurrentOwnerId(UUID ownerId);

    /**
     * Find serial numbers by batch ID (for Merkle tree calculation)
     */
    @Query("SELECT u.serialNumber FROM UnitItem u WHERE u.batch.id = :batchId ORDER BY u.serialNumber")
    List<String> findSerialNumbersByBatchId(@Param("batchId") UUID batchId);

    /**
     * Idempotent insert using native query with ON CONFLICT DO NOTHING
     * This ensures crash-safety for bulk unit generation
     */
    @Modifying
    @Query(value = "INSERT INTO unit_items (id, serial_number, batch_id, unit_type, digital_signature, " +
                   "status, scan_count, max_scan_limit, is_active) " +
                   "VALUES (:#{#item.id}, :#{#item.serialNumber}, :#{#item.batch.id}, " +
                   "CAST(:#{#item.unitType} AS VARCHAR), :#{#item.digitalSignature}, " +
                   "CAST(:#{#item.status} AS VARCHAR), :#{#item.scanCount}, :#{#item.maxScanLimit}, :#{#item.isActive}) " +
                   "ON CONFLICT (serial_number) DO NOTHING",
           nativeQuery = true)
    void saveIdempotent(@Param("item") UnitItem item);

    /**
     * Increment scan count
     */
    @Modifying
    @Query("UPDATE UnitItem u SET u.scanCount = u.scanCount + 1, u.lastScannedAt = CURRENT_TIMESTAMP " +
           "WHERE u.id = :unitId")
    void incrementScanCount(@Param("unitId") UUID unitId);

    /**
     * Update unit status
     */
    @Modifying
    @Query("UPDATE UnitItem u SET u.status = :status WHERE u.id = :unitId")
    void updateStatus(@Param("unitId") UUID unitId, @Param("status") UnitItem.UnitStatus status);

    /**
     * Deactivate unit (for kill-switch)
     */
    @Modifying
    @Query("UPDATE UnitItem u SET u.isActive = false, u.status = 'RECALLED_AUTO' WHERE u.id = :unitId")
    void deactivateUnit(@Param("unitId") UUID unitId);

    /**
     * Update current owner
     */
    @Modifying
    @Query("UPDATE UnitItem u SET u.currentOwner.id = :ownerId WHERE u.id = :unitId")
    void updateCurrentOwner(@Param("unitId") UUID unitId, @Param("ownerId") UUID ownerId);

    /**
     * Find units exceeding scan limit
     */
    @Query("SELECT u FROM UnitItem u WHERE u.scanCount > u.maxScanLimit AND u.isActive = true")
    List<UnitItem> findUnitsExceedingScanLimit();

    /**
     * Count units by batch
     */
    long countByBatchId(UUID batchId);

    /**
     * Cascade recall to all child units
     */
    @Modifying
    @Query("UPDATE UnitItem u SET u.status = 'RECALLED', u.isActive = false " +
           "WHERE u.parentUnit.id = :parentUnitId")
    void cascadeRecallToChildren(@Param("parentUnitId") UUID parentUnitId);
    
    /**
     * Find units by batch
     */
    List<UnitItem> findByBatch(pharmatrust.manufacturing_system.entity.Batch batch);
    
    /**
     * Count units by status
     */
    Long countByStatus(UnitItem.UnitStatus status);
    
    /**
     * Count units by current owner role
     */
    @Query("SELECT COUNT(u) FROM UnitItem u WHERE u.currentOwner.role = :role")
    Long countByCurrentOwnerRole(@Param("role") pharmatrust.manufacturing_system.entity.User.Role role);
    
    /**
     * Count flagged units (scan count >= max limit)
     */
    @Query("SELECT COUNT(u) FROM UnitItem u WHERE u.scanCount >= u.maxScanLimit")
    Long countFlaggedUnits();
}
