package pharmatrust.manufacturing_system.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.AuditLog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AuditLog entity operations
 * Supports compliance auditing and regulatory reporting
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs by user
     */
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find audit logs by entity type
     */
    Page<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    /**
     * Find audit logs by entity ID
     */
    List<AuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId);

    /**
     * Find audit logs by action
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    /**
     * Find audit logs within time range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    Page<AuditLog> findLogsBetween(@Param("startTime") Instant startTime, 
                                    @Param("endTime") Instant endTime, 
                                    Pageable pageable);

    /**
     * Find regulator access logs (for manufacturer transparency)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.role = 'REGULATOR' ORDER BY a.createdAt DESC")
    Page<AuditLog> findRegulatorAccessLogs(Pageable pageable);

    /**
     * Find audit logs by IP address
     */
    List<AuditLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress);

    /**
     * Count logs by user
     */
    long countByUserId(UUID userId);

    /**
     * Find latest 100 audit logs
     */
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
