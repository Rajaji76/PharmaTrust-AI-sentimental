package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * UnitItem entity representing individual pharmaceutical units with QR codes
 * Supports hierarchical packaging (Tablet → Strip → Box → Carton) and scan tracking
 */
@Entity
@Table(name = "unit_items", indexes = {
    @Index(name = "idx_unit_items_serial_number", columnList = "serial_number"),
    @Index(name = "idx_unit_items_batch_id", columnList = "batch_id"),
    @Index(name = "idx_unit_items_parent_unit_id", columnList = "parent_unit_id"),
    @Index(name = "idx_unit_items_status", columnList = "status"),
    @Index(name = "idx_unit_items_current_owner_id", columnList = "current_owner_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 255)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_unit_id", referencedColumnName = "id")
    private UnitItem parentUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 50)
    private UnitType unitType;

    @Column(name = "qr_payload_encrypted", columnDefinition = "TEXT")
    private String qrPayloadEncrypted;

    @Column(name = "digital_signature", columnDefinition = "TEXT")
    private String digitalSignature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UnitStatus status = UnitStatus.ACTIVE;

    @Column(name = "scan_count", nullable = false)
    @Builder.Default
    private Integer scanCount = 0;

    @Column(name = "max_scan_limit", nullable = false)
    @Builder.Default
    private Integer maxScanLimit = 5;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "first_scanned_at")
    private Instant firstScannedAt;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_owner_id")
    private User currentOwner;

    public enum UnitType {
        TABLET,
        STRIP,
        BOX,
        CARTON
    }

    public enum UnitStatus {
        ACTIVE,
        TRANSFERRED,
        RECALLED,
        RECALLED_AUTO,
        EXPIRED
    }
}
