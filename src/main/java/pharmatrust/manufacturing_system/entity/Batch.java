package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Batch entity representing a pharmaceutical batch with blockchain integration
 * Supports multi-signature approval workflow and Merkle root verification
 */
@Entity
@Table(name = "batches", indexes = {
    @Index(name = "idx_batches_batch_number", columnList = "batch_number"),
    @Index(name = "idx_batches_manufacturer_id", columnList = "manufacturer_id"),
    @Index(name = "idx_batches_status", columnList = "status"),
    @Index(name = "idx_batches_expiry_date", columnList = "expiry_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_number", nullable = false, unique = true, length = 255)
    private String batchNumber;

    @Column(name = "medicine_name", nullable = false, length = 255)
    private String medicineName;

    @Column(name = "manufacturing_date", nullable = false)
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private User manufacturer;

    @Column(name = "lab_report_hash", length = 255)
    private String labReportHash;

    @Column(name = "lab_report_s3_key", length = 500)
    private String labReportS3Key;

    @Column(name = "blockchain_tx_id", length = 255)
    private String blockchainTxId;

    @Column(name = "merkle_root", length = 255)
    private String merkleRoot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private BatchStatus status = BatchStatus.PROCESSING;

    @Column(name = "total_units", nullable = false)
    @Builder.Default
    private Integer totalUnits = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "digital_signature", columnDefinition = "TEXT")
    private String digitalSignature;

    @Column(name = "expiry_alert_sent", nullable = false)
    @Builder.Default
    private Boolean expiryAlertSent = false;

    @Column(name = "expiry_warning_date")
    private LocalDate expiryWarningDate;

    public enum BatchStatus {
        PROCESSING,
        PENDING_APPROVAL,
        ACTIVE,
        QUARANTINE,
        RECALLED,
        RECALLED_AUTO
    }

    @PrePersist
    protected void onCreate() {
        if (expiryWarningDate == null && expiryDate != null) {
            expiryWarningDate = expiryDate.minusDays(30);
        }
    }
}
