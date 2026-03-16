package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * RecallEvent entity for tracking batch recalls (manual and automatic)
 * Supports emergency kill-switch and AI Sentinel auto-recalls
 */
@Entity
@Table(name = "recall_events", indexes = {
    @Index(name = "idx_recall_events_batch_id", columnList = "batch_id"),
    @Index(name = "idx_recall_events_initiated_by", columnList = "initiated_by"),
    @Index(name = "idx_recall_events_initiated_at", columnList = "initiated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "recall_type", nullable = false, length = 50)
    private RecallType recallType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private RecallStatus status = RecallStatus.ACTIVE;

    public enum RecallType {
        MANUAL,
        AUTO,
        EMERGENCY
    }

    public enum RecallStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}
