package pharmatrust.manufacturing_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * BatchJob entity for tracking asynchronous job processing (RabbitMQ)
 * Supports unit generation, QR generation, and blockchain minting jobs
 */
@Entity
@Table(name = "batch_jobs", indexes = {
    @Index(name = "idx_batch_jobs_batch_id", columnList = "batch_id"),
    @Index(name = "idx_batch_jobs_status", columnList = "status"),
    @Index(name = "idx_batch_jobs_job_type", columnList = "job_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private Batch batch;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "total_items", nullable = false)
    @Builder.Default
    private Integer totalItems = 0;

    @Column(name = "processed_items", nullable = false)
    @Builder.Default
    private Integer processedItems = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum JobType {
        UNIT_GENERATION,
        QR_GENERATION,
        BLOCKCHAIN_MINT
    }

    public enum JobStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    /**
     * Calculate job progress percentage
     */
    public double getProgressPercentage() {
        if (totalItems == 0) return 0.0;
        return (processedItems * 100.0) / totalItems;
    }
}
