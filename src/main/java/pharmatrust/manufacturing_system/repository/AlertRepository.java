package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.Alert;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Alert entity operations
 * Supports AI Sentinel alerts and regulator notifications
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /**
     * Find alerts by type
     */
    List<Alert> findByAlertTypeOrderByCreatedAtDesc(Alert.AlertType alertType);

    /**
     * Find alerts by severity
     */
    List<Alert> findBySeverityOrderByCreatedAtDesc(Alert.Severity severity);

    /**
     * Find unacknowledged alerts
     */
    @Query("SELECT a FROM Alert a WHERE a.acknowledged = false ORDER BY a.severity DESC, a.createdAt DESC")
    List<Alert> findUnacknowledgedAlerts();

    /**
     * Find critical alerts
     */
    @Query("SELECT a FROM Alert a WHERE a.severity = 'CRITICAL' AND a.acknowledged = false ORDER BY a.createdAt DESC")
    List<Alert> findCriticalAlerts();

    /**
     * Find alerts for entity
     */
    List<Alert> findByEntityIdOrderByCreatedAtDesc(UUID entityId);

    /**
     * Find fraud alerts (for regulator dashboard)
     */
    @Query("SELECT a FROM Alert a WHERE a.alertType IN ('GEOGRAPHIC_FRAUD', 'SUSPICIOUS_SCAN', 'TAMPERING_DETECTED') " +
           "ORDER BY a.createdAt DESC")
    List<Alert> findFraudAlerts();

    /**
     * Count unacknowledged alerts by severity
     */
    long countByAcknowledgedFalseAndSeverity(Alert.Severity severity);
}
