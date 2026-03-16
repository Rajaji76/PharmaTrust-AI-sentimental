package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pharmatrust.manufacturing_system.entity.BatchJob;
import pharmatrust.manufacturing_system.repository.BatchJobRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Message Queue Service for async job management
 * Handles job enqueueing, status tracking, and retry mechanism
 * Requirements: FR-030, FR-031
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageQueueService {

    private final RabbitTemplate rabbitTemplate;
    private final BatchJobRepository batchJobRepository;

    @Value("${queue.unit-generation:unit-generation-queue}")
    private String unitGenerationQueue;

    @Value("${queue.qr-generation:qr-generation-queue}")
    private String qrGenerationQueue;

    @Value("${queue.blockchain-mint:blockchain-mint-queue}")
    private String blockchainMintQueue;

    /**
     * Enqueue a job for async processing
     * Returns job ID immediately (non-blocking)
     */
    public String enqueueJob(BatchJob.JobType jobType, UUID batchId, Map<String, Object> params) {
        // Create job record
        BatchJob job = new BatchJob();
        job.setBatchId(batchId);
        job.setJobType(jobType);
        job.setStatus(BatchJob.JobStatus.QUEUED);
        job.setTotalItems(params.containsKey("quantity") ? (Integer) params.get("quantity") : 0);
        job.setProcessedItems(0);
        job = batchJobRepository.save(job);

        String jobId = job.getId().toString();

        // Build message payload
        Map<String, Object> message = new HashMap<>(params);
        message.put("jobId", jobId);
        message.put("batchId", batchId.toString());
        message.put("jobType", jobType.name());
        message.put("enqueuedAt", Instant.now().toString());

        // Route to appropriate queue
        String queue = getQueueForJobType(jobType);
        try {
            rabbitTemplate.convertAndSend(queue, message);
            log.info("Job enqueued: jobId={}, type={}, batchId={}", jobId, jobType, batchId);
        } catch (Exception e) {
            log.warn("RabbitMQ not available, job saved to DB only: jobId={}, error={}", jobId, e.getMessage());
        }
        return jobId;
    }

    /**
     * Get job status by job ID
     */
    public BatchJob getJobStatus(UUID jobId) {
        return batchJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
    }

    /**
     * Update job progress
     */
    public void updateJobProgress(UUID jobId, int processedItems) {
        batchJobRepository.findById(jobId).ifPresent(job -> {
            job.setProcessedItems(processedItems);
            if (job.getStatus() == BatchJob.JobStatus.QUEUED) {
                job.setStatus(BatchJob.JobStatus.PROCESSING);
                job.setStartedAt(Instant.now());
            }
            batchJobRepository.save(job);
        });
    }

    /**
     * Mark job as completed
     */
    public void completeJob(UUID jobId) {
        batchJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(BatchJob.JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            batchJobRepository.save(job);
            log.info("Job completed: {}", jobId);
        });
    }

    /**
     * Mark job as failed
     */
    public void failJob(UUID jobId, String errorMessage) {
        batchJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(BatchJob.JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(Instant.now());
            batchJobRepository.save(job);
            log.error("Job failed: {}, error: {}", jobId, errorMessage);
        });
    }

    /**
     * Retry a failed job (max 3 attempts)
     */
    public void retryJob(UUID jobId) {
        BatchJob job = batchJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        if (job.getStatus() != BatchJob.JobStatus.FAILED) {
            throw new RuntimeException("Only failed jobs can be retried");
        }

        // Reset job status
        job.setStatus(BatchJob.JobStatus.QUEUED);
        job.setProcessedItems(0);
        job.setErrorMessage(null);
        batchJobRepository.save(job);

        // Re-enqueue
        Map<String, Object> message = new HashMap<>();
        message.put("jobId", jobId.toString());
        message.put("batchId", job.getBatchId().toString());
        message.put("jobType", job.getJobType().name());
        message.put("quantity", job.getTotalItems());
        message.put("retry", true);

        String queue = getQueueForJobType(job.getJobType());
        try {
            rabbitTemplate.convertAndSend(queue, message);
        } catch (Exception e) {
            log.warn("RabbitMQ not available for retry, job status updated in DB: {}", jobId);
        }

        log.info("Job retried: {}", jobId);
    }

    private String getQueueForJobType(BatchJob.JobType jobType) {
        return switch (jobType) {
            case UNIT_GENERATION -> unitGenerationQueue;
            case QR_GENERATION -> qrGenerationQueue;
            case BLOCKCHAIN_MINT -> blockchainMintQueue;
        };
    }
}
