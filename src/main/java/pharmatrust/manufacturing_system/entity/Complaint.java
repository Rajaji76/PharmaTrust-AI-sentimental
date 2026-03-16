package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Complaint raised by Distributor or Retailer during manual inspection.
 * AI analyzes the complaint text and raises alert to Regulator.
 */
@Entity
@Table(name = "complaints", indexes = {
    @Index(name = "idx_complaints_reporter", columnList = "reporter_id"),
    @Index(name = "idx_complaints_serial", columnList = "serial_number"),
    @Index(name = "idx_complaints_status", columnList = "status"),
    @Index(name = "idx_complaints_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Who raised the complaint (Distributor or Retailer)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // The box/unit serial number being inspected
    @Column(name = "serial_number", nullable = false, length = 200)
    private String serialNumber;

    // Batch number (derived from serial)
    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    // Medicine name
    @Column(name = "medicine_name", length = 200)
    private String medicineName;

    // Issue type selected by user
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 50)
    private IssueType issueType;

    // Free-text description of the issue
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    // AI-analyzed severity
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_severity", length = 20)
    private Alert.Severity aiSeverity;

    // AI analysis result text
    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    // Alert raised to regulator
    @Column(name = "alert_id")
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum IssueType {
        DAMAGED_BOX,
        TEMPERATURE_ISSUE,
        SEAL_BROKEN,
        WRONG_MEDICINE,
        EXPIRED_STOCK,
        SUSPICIOUS_APPEARANCE,
        QUANTITY_MISMATCH,
        OTHER
    }

    public enum Status {
        OPEN,
        UNDER_REVIEW,
        RESOLVED,
        ESCALATED
    }
}
