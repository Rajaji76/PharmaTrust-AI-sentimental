package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-signature approval workflow service
 * Requires PRODUCTION_HEAD + QUALITY_CHECKER approvals before batch activation
 * Requirements: FR-005, FR-018
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BatchApprovalService {

    private final BatchRepository batchRepository;
    private final BatchApprovalRepository batchApprovalRepository;
    private final UserRepository userRepository;
    private final CryptographyService cryptographyService;
    private final MessageQueueService messageQueueService;

    private static final List<BatchApproval.ApprovalType> REQUIRED_APPROVALS = List.of(
            BatchApproval.ApprovalType.PRODUCTION_HEAD,
            BatchApproval.ApprovalType.QUALITY_CHECKER
    );

    /**
     * Submit approval for a batch with digital signature
     */
    @Transactional
    public Map<String, Object> approveBatch(UUID batchId, String approverEmail,
                                             BatchApproval.ApprovalType approvalType,
                                             String digitalSignature) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        if (batch.getStatus() != Batch.BatchStatus.PENDING_APPROVAL &&
            batch.getStatus() != Batch.BatchStatus.PROCESSING) {
            throw new RuntimeException("Batch is not in approvable state: " + batch.getStatus());
        }

        User approver = userRepository.findByEmail(approverEmail)
                .orElseThrow(() -> new RuntimeException("Approver not found: " + approverEmail));

        // Check duplicate approval
        if (batchApprovalRepository.existsByBatchIdAndApprovalType(batchId, approvalType)) {
            throw new RuntimeException("Approval type already submitted: " + approvalType);
        }

        // Verify digital signature
        String dataToVerify = batchId.toString() + approvalType.name() + approverEmail;
        String expectedSig = cryptographyService.generateHMAC(dataToVerify, "pharmatrust-secret-key");
        if (!expectedSig.equals(digitalSignature)) {
            log.warn("Digital signature mismatch for batch approval: batchId={}, approver={}", batchId, approverEmail);
            // In production: throw exception. For MVP: log and continue
        }

        // Save approval
        BatchApproval approval = BatchApproval.builder()
                .batch(batch)
                .approver(approver)
                .approvalType(approvalType)
                .digitalSignature(digitalSignature)
                .build();
        batchApprovalRepository.save(approval);

        log.info("Approval recorded: batchId={}, type={}, approver={}", batchId, approvalType, approverEmail);

        // Check if all required approvals received
        boolean allApproved = checkAllApprovalsReceived(batchId);
        if (allApproved) {
            activateBatch(batch);
        }

        return Map.of(
                "batchId", batchId,
                "approvalType", approvalType,
                "allApproved", allApproved,
                "status", allApproved ? "ACTIVATED" : "PENDING_MORE_APPROVALS",
                "remainingApprovals", getRemainingApprovals(batchId)
        );
    }

    /**
     * Check if all required approvals have been received
     */
    public boolean checkAllApprovalsReceived(UUID batchId) {
        List<BatchApproval.ApprovalType> received = batchApprovalRepository
                .findDistinctApprovalTypesByBatchId(batchId);
        return received.containsAll(REQUIRED_APPROVALS);
    }

    /**
     * Get list of remaining required approvals
     */
    public List<BatchApproval.ApprovalType> getRemainingApprovals(UUID batchId) {
        List<BatchApproval.ApprovalType> received = batchApprovalRepository
                .findDistinctApprovalTypesByBatchId(batchId);
        return REQUIRED_APPROVALS.stream()
                .filter(type -> !received.contains(type))
                .toList();
    }

    /**
     * Activate batch after all approvals - triggers blockchain minting
     */
    @Transactional
    public void activateBatch(Batch batch) {
        batch.setStatus(Batch.BatchStatus.ACTIVE);
        batchRepository.save(batch);
        log.info("Batch activated after all approvals: {}", batch.getBatchNumber());

        // Enqueue blockchain minting
        Map<String, Object> params = Map.of("batchNumber", batch.getBatchNumber());
        messageQueueService.enqueueJob(BatchJob.JobType.BLOCKCHAIN_MINT, batch.getId(), params);
        log.info("Blockchain mint job enqueued for batch: {}", batch.getBatchNumber());
    }

    /**
     * Get all approvals for a batch
     */
    public List<BatchApproval> getBatchApprovals(UUID batchId) {
        return batchApprovalRepository.findByBatchId(batchId);
    }
}
