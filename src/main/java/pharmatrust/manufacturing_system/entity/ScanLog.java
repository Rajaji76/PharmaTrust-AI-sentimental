package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * ScanLog entity for tracking all QR code scans with anomaly detection
 * Supports geographic fraud detection and device fingerprinting
 */
@Entity
@Table(name = "scan_logs", indexes = {
    @Index(name = "idx_scan_logs_unit_id", columnList = "unit_id"),
    @Index(name = "idx_scan_logs_scanned_at", columnList = "scanned_at"),
    @Index(name = "idx_scan_logs_scan_result", columnList = "scan_result"),
    @Index(name = "idx_scan_logs_anomaly_score", columnList = "anomaly_score")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitItem unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scanned_by_user_id")
    private User scannedByUser;

    @CreationTimestamp
    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt;

    @Column(name = "location_lat", length = 50)
    private String locationLat;

    @Column(name = "location_lng", length = 50)
    private String locationLng;

    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_result", nullable = false, length = 50)
    private ScanResult scanResult;

    @Column(name = "anomaly_score")
    @Builder.Default
    private Float anomalyScore = 0.0f;

    @Column(name = "auto_flagged", nullable = false)
    @Builder.Default
    private Boolean autoFlagged = false;

    public enum ScanResult {
        VALID,
        INVALID,
        SUSPICIOUS,
        FLAGGED
    }
}
