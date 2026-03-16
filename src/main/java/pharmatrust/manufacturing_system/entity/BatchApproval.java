package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * BatchApproval entity for multi-signature approval workflow
 * Requires approvals from PRODUCTION_HEAD and QUALITY_CHECKER before batch activation
 */
@Entity
@Table(name = "batch_approvals", indexes = {
    @Index(name = "idx_batch_approvals_batch_id", columnList = "batch_id"),
    @Index(name = "idx_batch_approvals_approver_id", columnList = "approver_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 50)
    private ApprovalType approvalType;

    @Column(name = "digital_signature", nullable = false, columnDefinition = "TEXT")
    private String digitalSignature;

    @CreationTimestamp
    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    public enum ApprovalType {
        PRODUCTION_HEAD,
        QUALITY_CHECKER,
        REGULATOR
    }
}
