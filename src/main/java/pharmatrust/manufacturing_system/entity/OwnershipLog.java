package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * OwnershipLog entity for tracking unit ownership transfers through supply chain
 * Enables complete traceability timeline from manufacturer to patient
 */
@Entity
@Table(name = "ownership_logs", indexes = {
    @Index(name = "idx_ownership_logs_unit_id", columnList = "unit_id"),
    @Index(name = "idx_ownership_logs_from_user_id", columnList = "from_user_id"),
    @Index(name = "idx_ownership_logs_to_user_id", columnList = "to_user_id"),
    @Index(name = "idx_ownership_logs_transferred_at", columnList = "transferred_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnershipLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitItem unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id")
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 50)
    private TransferType transferType;

    @CreationTimestamp
    @Column(name = "transferred_at", nullable = false, updatable = false)
    private Instant transferredAt;

    @Column(length = 255)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum TransferType {
        MANUFACTURE_TO_DISTRIBUTOR,
        DISTRIBUTOR_TO_PHARMACY,
        PHARMACY_TO_PATIENT
    }
}
