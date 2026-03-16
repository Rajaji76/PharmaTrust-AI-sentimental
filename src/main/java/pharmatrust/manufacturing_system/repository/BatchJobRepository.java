package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.BatchJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BatchJob entity operations
 * Supports async job tracking and progress monitoring
 */
@Repository
public interface BatchJobRepository extends JpaRepository<BatchJob, UUID> {

    /**
     * Find jobs by batch
     */
    List<BatchJob> findByBatchIdOrderByStartedAtDesc(UUID batchId);

    /**
     * Find jobs by status
     */
    List<BatchJob> findByStatusOrderByStartedAtDesc(BatchJob.JobStatus status);

    /**
     * Find jobs by type
     */
    List<BatchJob> findByJobTypeOrderByStartedAtDesc(BatchJob.JobType jobType);

    /**
     * Find queued jobs (for worker processing)
     */
    @Query("SELECT j FROM BatchJob j WHERE j.status = 'QUEUED' ORDER BY j.id ASC")
    List<BatchJob> findQueuedJobs();

    /**
     * Find failed jobs (for retry)
     */
    @Query("SELECT j FROM BatchJob j WHERE j.status = 'FAILED' ORDER BY j.startedAt DESC")
    List<BatchJob> findFailedJobs();

    /**
     * Find processing jobs
     */
    @Query("SELECT j FROM BatchJob j WHERE j.status = 'PROCESSING' ORDER BY j.startedAt DESC")
    List<BatchJob> findProcessingJobs();

    /**
     * Find latest job for batch and type
     */
    Optional<BatchJob> findFirstByBatchIdAndJobTypeOrderByStartedAtDesc(UUID batchId, BatchJob.JobType jobType);

    /**
     * Count jobs by status
     */
    long countByStatus(BatchJob.JobStatus status);

    /**
     * Find jobs by batch and status
     */
    List<BatchJob> findByBatchIdAndStatus(UUID batchId, BatchJob.JobStatus status);
}
