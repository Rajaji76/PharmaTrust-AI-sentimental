package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.entity.BatchJob;
import pharmatrust.manufacturing_system.repository.BatchJobRepository;
import pharmatrust.manufacturing_system.service.MessageQueueService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Job status and monitoring endpoints
 * Requirements: FR-031
 */
@RestController
@RequestMapping("/api/v1/jobs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final MessageQueueService messageQueueService;
    private final BatchJobRepository batchJobRepository;

    /**
     * GET /api/v1/jobs/{jobId} - Get job status
     */
    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getJobStatus(@PathVariable UUID jobId) {
        try {
            BatchJob job = messageQueueService.getJobStatus(jobId);
            return ResponseEntity.ok(Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus().name(),
                    "jobType", job.getJobType().name(),
                    "totalItems", job.getTotalItems(),
                    "processedItems", job.getProcessedItems(),
                    "progressPercent", job.getProgressPercentage(),
                    "startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null,
                    "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                    "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : ""
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/v1/jobs/batch/{batchId} - Get all jobs for a batch
     */
    @GetMapping("/batch/{batchId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBatchJobs(@PathVariable UUID batchId) {
        List<BatchJob> jobs = batchJobRepository.findByBatchIdOrderByStartedAtDesc(batchId);
        List<Map<String, Object>> jobList = jobs.stream().map(job -> Map.<String, Object>of(
                "jobId", job.getId(),
                "status", job.getStatus().name(),
                "jobType", job.getJobType().name(),
                "progressPercent", job.getProgressPercentage(),
                "totalItems", job.getTotalItems(),
                "processedItems", job.getProcessedItems()
        )).toList();

        return ResponseEntity.ok(Map.of("batchId", batchId, "jobs", jobList));
    }

    /**
     * POST /api/v1/jobs/{jobId}/retry - Retry a failed job
     */
    @PostMapping("/{jobId}/retry")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER','REGULATOR')")
    public ResponseEntity<?> retryJob(@PathVariable UUID jobId) {
        try {
            messageQueueService.retryJob(jobId);
            return ResponseEntity.ok(Map.of("success", true, "jobId", jobId, "message", "Job queued for retry"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/jobs/{jobId}/cancel - Cancel a queued job
     */
    @PostMapping("/{jobId}/cancel")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER','REGULATOR')")
    public ResponseEntity<?> cancelJob(@PathVariable UUID jobId) {
        try {
            messageQueueService.failJob(jobId, "Cancelled by user");
            return ResponseEntity.ok(Map.of("success", true, "jobId", jobId, "message", "Job cancelled"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/jobs/my-jobs - Get all jobs for the current user's batches
     */
    @GetMapping("/my-jobs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyJobs(org.springframework.security.core.Authentication authentication) {
        List<BatchJob> allJobs = batchJobRepository.findAll(
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));
        List<Map<String, Object>> result = allJobs.stream().map(job -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("jobId", job.getId());
            m.put("status", job.getStatus().name());
            m.put("jobType", job.getJobType().name());
            m.put("progress", job.getProgressPercentage());
            m.put("totalItems", job.getTotalItems());
            m.put("processedItems", job.getProcessedItems());
            m.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
            m.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
            m.put("errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : "");
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }
}

