package pharmatrust.manufacturing_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pharmatrust.manufacturing_system.config.JwtAuthenticationFilter;
import pharmatrust.manufacturing_system.config.SecurityConfig;
import pharmatrust.manufacturing_system.entity.BatchJob;
import pharmatrust.manufacturing_system.repository.BatchJobRepository;
import pharmatrust.manufacturing_system.service.JwtService;
import pharmatrust.manufacturing_system.service.MessageQueueService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for JobController
 * Tests: job status, retry, cancel endpoints, access control
 * Requirements: FR-031
 */
@WebMvcTest(JobController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageQueueService messageQueueService;

    @MockBean
    private BatchJobRepository batchJobRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private BatchJob job;
    private UUID jobId;
    private UUID batchId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        job = BatchJob.builder()
                .id(jobId)
                .batchId(batchId)
                .jobType(BatchJob.JobType.UNIT_GENERATION)
                .status(BatchJob.JobStatus.PROCESSING)
                .totalItems(1000)
                .processedItems(500)
                .startedAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now())
                .errorMessage("")
                .build();
    }

    // ==================== GET /api/v1/jobs/{jobId} ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getJobStatus_existingJob_returns200() throws Exception {
        when(messageQueueService.getJobStatus(any())).thenReturn(job);

        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.jobType").value("UNIT_GENERATION"))
                .andExpect(jsonPath("$.totalItems").value(1000))
                .andExpect(jsonPath("$.processedItems").value(500))
                .andExpect(jsonPath("$.progressPercent").value(50.0));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getJobStatus_completedJob_returnsCompletedAt() throws Exception {
        job.setStatus(BatchJob.JobStatus.COMPLETED);
        job.setProcessedItems(1000);
        job.setCompletedAt(Instant.now());

        when(messageQueueService.getJobStatus(any())).thenReturn(job);

        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progressPercent").value(100.0))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getJobStatus_unknownJob_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(messageQueueService.getJobStatus(unknownId))
                .thenThrow(new RuntimeException("Job not found"));

        mockMvc.perform(get("/api/v1/jobs/{jobId}", unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobStatus_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().is4xxClientError());
    }

    // ==================== GET /api/v1/jobs/batch/{batchId} ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getBatchJobs_existingBatch_returnsJobList() throws Exception {
        when(batchJobRepository.findByBatchIdOrderByStartedAtDesc(batchId)).thenReturn(List.of(job));

        mockMvc.perform(get("/api/v1/jobs/batch/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.jobs").isArray())
                .andExpect(jsonPath("$.jobs[0].jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.jobs[0].status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getBatchJobs_noJobs_returnsEmptyList() throws Exception {
        when(batchJobRepository.findByBatchIdOrderByStartedAtDesc(batchId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs/batch/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs").isEmpty());
    }

    // ==================== POST /api/v1/jobs/{jobId}/retry ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void retryJob_failedJob_returns200() throws Exception {
        doNothing().when(messageQueueService).retryJob(jobId);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/retry", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.message").value("Job queued for retry"));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void retryJob_unknownJob_returns400() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(messageQueueService.getJobStatus(unknownId))
                .thenThrow(new RuntimeException("Job not found"));
        org.mockito.Mockito.doThrow(new RuntimeException("Job not found"))
                .when(messageQueueService).retryJob(unknownId);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/retry", unknownId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(username = "distributor@test.com", authorities = {"DISTRIBUTOR"})
    void retryJob_distributorRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/{jobId}/retry", jobId))
                .andExpect(status().is4xxClientError());
    }

    // ==================== POST /api/v1/jobs/{jobId}/cancel ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void cancelJob_queuedJob_returns200() throws Exception {
        doNothing().when(messageQueueService).failJob(jobId, "Cancelled by user");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/cancel", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Job cancelled"));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void cancelJob_unknownJob_returns400() throws Exception {
        UUID unknownId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new RuntimeException("Job not found"))
                .when(messageQueueService).failJob(unknownId, "Cancelled by user");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/cancel", unknownId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(username = "regulator@test.com", authorities = {"REGULATOR"})
    void cancelJob_regulatorRole_returns200() throws Exception {
        doNothing().when(messageQueueService).failJob(jobId, "Cancelled by user");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/cancel", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
