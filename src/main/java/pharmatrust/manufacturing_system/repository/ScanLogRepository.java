package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.ScanLog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ScanLog entity operations
 * Supports anomaly detection, geographic fraud analysis, and scan history
 */
@Repository
public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    /**
     * Find recent scans for a unit (for anomaly detection)
     */
    @Query("SELECT s FROM ScanLog s WHERE s.unit.id = :unitId ORDER BY s.scannedAt DESC")
    List<ScanLog> findTop5ByUnitIdOrderByScannedAtDesc(@Param("unitId") UUID unitId);

    /**
     * Find all scans for a unit
     */
    List<ScanLog> findByUnitIdOrderByScannedAtDesc(UUID unitId);

    /**
     * Find scans by result type
     */
    List<ScanLog> findByScanResult(ScanLog.ScanResult scanResult);

    /**
     * Find auto-flagged scans
     */
    @Query("SELECT s FROM ScanLog s WHERE s.autoFlagged = true ORDER BY s.scannedAt DESC")
    List<ScanLog> findAutoFlaggedScans();

    /**
     * Find scans with high anomaly scores
     */
    @Query("SELECT s FROM ScanLog s WHERE s.anomalyScore >= :threshold ORDER BY s.anomalyScore DESC, s.scannedAt DESC")
    List<ScanLog> findHighAnomalyScans(@Param("threshold") Float threshold);

    /**
     * Find scans by user
     */
    List<ScanLog> findByScannedByUserIdOrderByScannedAtDesc(UUID userId);

    /**
     * Find scans within time range
     */
    @Query("SELECT s FROM ScanLog s WHERE s.scannedAt BETWEEN :startTime AND :endTime ORDER BY s.scannedAt DESC")
    List<ScanLog> findScansBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Count scans for a unit
     */
    long countByUnitId(UUID unitId);

    /**
     * Find suspicious scans (for regulator dashboard)
     */
    @Query("SELECT s FROM ScanLog s WHERE s.scanResult IN ('SUSPICIOUS', 'FLAGGED') ORDER BY s.scannedAt DESC")
    List<ScanLog> findSuspiciousScans();
    
    /**
     * Count scans by result type
     */
    Long countByScanResult(ScanLog.ScanResult scanResult);

    /**
     * Find latest 50 scans across all units (for scan history endpoint)
     */
    List<ScanLog> findTop50ByOrderByScannedAtDesc();
}
