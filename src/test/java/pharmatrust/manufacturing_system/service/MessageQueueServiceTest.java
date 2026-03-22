package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import pharmatrust.manufacturing_system.entity.BatchJob;
import pharmatrust.manufacturing_system.repository.BatchJobRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageQueueService
 * Tests: job enqueueing, status tracking, retry mechanism
 * Requirements: FR-030, FR-031
 */
@ExtendWith(MockitoExtension.class)
class MessageQueueServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private BatchJobRepository batchJobRepository;

    @InjectMocks
    private MessageQueueService messageQueueService;

    private UUID batchId;
    private UUID jobId;
    private BatchJob savedJob;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(messageQueueService, "unitGenerationQueue", "unit-generation-queue");
        ReflectionTestUtils.setField(messageQueueService, "qrGenerationQueue", "qr-generation-queue");
        ReflectionTestUtils.setField(messageQueueService, "blockchainMintQueue", "blockchain-mint-queue");

        batchId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        savedJob = new BatchJob();
        savedJob.setId(jobId);
        savedJob.setBatchId(batchId);
        savedJob.setJobType(BatchJob.JobType.UNIT_GENERATION);
        savedJob.setStatus(BatchJob.JobStatus.QUEUED);
        savedJob.setTotalItems(1000);
        savedJob.setProcessedItems(0);
    }

    // ==================== Enqueue Job ====================

    @Test
    void enqueueJob_unitGeneration_returnsJobId() {
        when(batchJobRepository.save(any(BatchJob.class))).thenReturn(savedJob);

        String jobId = messageQueueService.enqueueJob(
                BatchJob.JobType.UNIT_GENERATION,
                batchId,
                Map.of("quantity", 1000));

        assertThat(jobId).isNotNull().isNotEmpty();
        verify(batchJobRepository).save(any(BatchJob.class));
    }

    @Test
    void enqueueJob_sendsToCorrectQueue_unitGeneration() {
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.enqueueJob(BatchJob.JobType.UNIT_GENERATION, batchId, Map.of());

        verify(rabbitTemplate).convertAndSend(eq("unit-generation-queue"), any(Map.class));
    }

    @Test
    void enqueueJob_sendsToCorrectQueue_qrGeneration() {
        savedJob.setJobType(BatchJob.JobType.QR_GENERATION);
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.enqueueJob(BatchJob.JobType.QR_GENERATION, batchId, Map.of());

        verify(rabbitTemplate).convertAndSend(eq("qr-generation-queue"), any(Map.class));
    }

    @Test
    void enqueueJob_sendsToCorrectQueue_blockchainMint() {
        savedJob.setJobType(BatchJob.JobType.BLOCKCHAIN_MINT);
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.enqueueJob(BatchJob.JobType.BLOCKCHAIN_MINT, batchId, Map.of());

        verify(rabbitTemplate).convertAndSend(eq("blockchain-mint-queue"), any(Map.class));
    }

    @Test
    void enqueueJob_rabbitMQUnavailable_stillSavesJobToDb() {
        when(batchJobRepository.save(any())).thenReturn(savedJob);
        doThrow(new RuntimeException("RabbitMQ unavailable"))
                .when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

        // Should not throw - gracefully handles RabbitMQ being down
        String jobId = messageQueueService.enqueueJob(
                BatchJob.JobType.UNIT_GENERATION, batchId, Map.of());

        assertThat(jobId).isNotNull();
        verify(batchJobRepository).save(any(BatchJob.class));
    }

    @Test
    void enqueueJob_setsQuantityFromParams() {
        when(batchJobRepository.save(any(BatchJob.class))).thenAnswer(inv -> {
            BatchJob job = inv.getArgument(0);
            assertThat(job.getTotalItems()).isEqualTo(5000);
            return savedJob;
        });

        messageQueueService.enqueueJob(BatchJob.JobType.UNIT_GENERATION, batchId, Map.of("quantity", 5000));
    }

    // ==================== Get Job Status ====================

    @Test
    void getJobStatus_existingJob_returnsJob() {
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));

        BatchJob result = messageQueueService.getJobStatus(jobId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(jobId);
        assertThat(result.getStatus()).isEqualTo(BatchJob.JobStatus.QUEUED);
    }

    @Test
    void getJobStatus_nonExistentJob_throwsException() {
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageQueueService.getJobStatus(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Job not found");
    }

    // ==================== Update Job Progress ====================

    @Test
    void updateJobProgress_updatesProcessedItems() {
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.updateJobProgress(jobId, 500);

        verify(batchJobRepository).save(argThat(job -> job.getProcessedItems() == 500));
    }

    @Test
    void updateJobProgress_firstUpdate_setsStatusToProcessing() {
        savedJob.setStatus(BatchJob.JobStatus.QUEUED);
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.updateJobProgress(jobId, 100);

        verify(batchJobRepository).save(argThat(job ->
                job.getStatus() == BatchJob.JobStatus.PROCESSING &&
                job.getStartedAt() != null));
    }

    // ==================== Complete Job ====================

    @Test
    void completeJob_setsStatusToCompleted() {
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.completeJob(jobId);

        verify(batchJobRepository).save(argThat(job ->
                job.getStatus() == BatchJob.JobStatus.COMPLETED &&
                job.getCompletedAt() != null));
    }

    // ==================== Fail Job ====================

    @Test
    void failJob_setsStatusToFailed() {
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.failJob(jobId, "Out of memory");

        verify(batchJobRepository).save(argThat(job ->
                job.getStatus() == BatchJob.JobStatus.FAILED &&
                "Out of memory".equals(job.getErrorMessage())));
    }

    // ==================== Retry Job ====================

    @Test
    void retryJob_failedJob_resetsAndRequeues() {
        savedJob.setStatus(BatchJob.JobStatus.FAILED);
        savedJob.setErrorMessage("Previous error");
        savedJob.setProcessedItems(500);

        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));
        when(batchJobRepository.save(any())).thenReturn(savedJob);

        messageQueueService.retryJob(jobId);

        verify(batchJobRepository).save(argThat(job ->
                job.getStatus() == BatchJob.JobStatus.QUEUED &&
                job.getProcessedItems() == 0 &&
                job.getErrorMessage() == null));
        verify(rabbitTemplate).convertAndSend(anyString(), any(Map.class));
    }

    @Test
    void retryJob_nonFailedJob_throwsException() {
        savedJob.setStatus(BatchJob.JobStatus.COMPLETED);
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(savedJob));

        assertThatThrownBy(() -> messageQueueService.retryJob(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only failed jobs can be retried");
    }
}
