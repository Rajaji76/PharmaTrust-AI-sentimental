package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchApprovalService
 * Tests: multi-signature approval workflow, batch activation, digital signature verification
 * Requirements: FR-005, FR-018
 */
@ExtendWith(MockitoExtension.class)
class BatchApprovalServiceTest {

    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchApprovalRepository batchApprovalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CryptographyService cryptographyService;
    @Mock
    private MessageQueueService messageQueueService;

    @InjectMocks
    private BatchApprovalService batchApprovalService;

    private UUID batchId;
    private Batch batch;
    private User productionHead;
    private User qualityChecker;

    @BeforeEach
    void setUp() {
        batchId = UUID.randomUUID();

        batch = new Batch();
        batch.setId(batchId);
        batch.setBatchNumber("BATCH001");
        batch.setStatus(Batch.BatchStatus.PENDING_APPROVAL);

        productionHead = new User();
        productionHead.setId(UUID.randomUUID());
        productionHead.setEmail("prodhead@pharma.com");
        productionHead.setRole(User.Role.MANUFACTURER);

        qualityChecker = new User();
        qualityChecker.setId(UUID.randomUUID());
        qualityChecker.setEmail("qc@pharma.com");
        qualityChecker.setRole(User.Role.MANUFACTURER);
    }

    // ==================== Approve Batch ====================

    @Test
    void approveBatch_firstApproval_returnsPendingMoreApprovals() {
        String signature = "valid-sig";
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(userRepository.findByEmail(productionHead.getEmail())).thenReturn(Optional.of(productionHead));
        when(batchApprovalRepository.existsByBatchIdAndApprovalType(batchId, BatchApproval.ApprovalType.PRODUCTION_HEAD))
                .thenReturn(false);
        when(cryptographyService.generateHMAC(anyString(), anyString())).thenReturn(signature);
        when(batchApprovalRepository.save(any())).thenReturn(new BatchApproval());
        // Only PRODUCTION_HEAD received so far
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of(BatchApproval.ApprovalType.PRODUCTION_HEAD));

        Map<String, Object> result = batchApprovalService.approveBatch(
                batchId, productionHead.getEmail(),
                BatchApproval.ApprovalType.PRODUCTION_HEAD, signature);

        assertThat(result.get("allApproved")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("PENDING_MORE_APPROVALS");
    }

    @Test
    void approveBatch_bothApprovals_activatesBatch() {
        String signature = "valid-sig";
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(userRepository.findByEmail(qualityChecker.getEmail())).thenReturn(Optional.of(qualityChecker));
        when(batchApprovalRepository.existsByBatchIdAndApprovalType(batchId, BatchApproval.ApprovalType.QUALITY_CHECKER))
                .thenReturn(false);
        when(cryptographyService.generateHMAC(anyString(), anyString())).thenReturn(signature);
        when(batchApprovalRepository.save(any())).thenReturn(new BatchApproval());
        // Both approvals received
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of(
                        BatchApproval.ApprovalType.PRODUCTION_HEAD,
                        BatchApproval.ApprovalType.QUALITY_CHECKER));
        when(batchRepository.save(any())).thenReturn(batch);
        when(messageQueueService.enqueueJob(any(), any(), any())).thenReturn("job-id");

        Map<String, Object> result = batchApprovalService.approveBatch(
                batchId, qualityChecker.getEmail(),
                BatchApproval.ApprovalType.QUALITY_CHECKER, signature);

        assertThat(result.get("allApproved")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("ACTIVATED");
        verify(batchRepository).save(argThat(b -> b.getStatus() == Batch.BatchStatus.ACTIVE));
    }

    @Test
    void approveBatch_batchNotFound_throwsException() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchApprovalService.approveBatch(
                batchId, "user@test.com",
                BatchApproval.ApprovalType.PRODUCTION_HEAD, "sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Batch not found");
    }

    @Test
    void approveBatch_batchNotInApprovableState_throwsException() {
        batch.setStatus(Batch.BatchStatus.ACTIVE);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchApprovalService.approveBatch(
                batchId, "user@test.com",
                BatchApproval.ApprovalType.PRODUCTION_HEAD, "sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not in approvable state");
    }

    @Test
    void approveBatch_duplicateApprovalType_throwsException() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(userRepository.findByEmail(productionHead.getEmail())).thenReturn(Optional.of(productionHead));
        when(batchApprovalRepository.existsByBatchIdAndApprovalType(batchId, BatchApproval.ApprovalType.PRODUCTION_HEAD))
                .thenReturn(true); // Already approved

        assertThatThrownBy(() -> batchApprovalService.approveBatch(
                batchId, productionHead.getEmail(),
                BatchApproval.ApprovalType.PRODUCTION_HEAD, "sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    void approveBatch_approverNotFound_throwsException() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchApprovalService.approveBatch(
                batchId, "unknown@test.com",
                BatchApproval.ApprovalType.PRODUCTION_HEAD, "sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Approver not found");
    }

    // ==================== Check All Approvals ====================

    @Test
    void checkAllApprovalsReceived_bothPresent_returnsTrue() {
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of(
                        BatchApproval.ApprovalType.PRODUCTION_HEAD,
                        BatchApproval.ApprovalType.QUALITY_CHECKER));

        boolean result = batchApprovalService.checkAllApprovalsReceived(batchId);

        assertThat(result).isTrue();
    }

    @Test
    void checkAllApprovalsReceived_onlyOnePresent_returnsFalse() {
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of(BatchApproval.ApprovalType.PRODUCTION_HEAD));

        boolean result = batchApprovalService.checkAllApprovalsReceived(batchId);

        assertThat(result).isFalse();
    }

    @Test
    void checkAllApprovalsReceived_nonePresent_returnsFalse() {
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of());

        boolean result = batchApprovalService.checkAllApprovalsReceived(batchId);

        assertThat(result).isFalse();
    }

    // ==================== Get Remaining Approvals ====================

    @Test
    void getRemainingApprovals_noneReceived_returnsBothRequired() {
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of());

        List<BatchApproval.ApprovalType> remaining = batchApprovalService.getRemainingApprovals(batchId);

        assertThat(remaining).containsExactlyInAnyOrder(
                BatchApproval.ApprovalType.PRODUCTION_HEAD,
                BatchApproval.ApprovalType.QUALITY_CHECKER);
    }

    @Test
    void getRemainingApprovals_productionHeadReceived_returnsQualityChecker() {
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of(BatchApproval.ApprovalType.PRODUCTION_HEAD));

        List<BatchApproval.ApprovalType> remaining = batchApprovalService.getRemainingApprovals(batchId);

        assertThat(remaining).containsExactly(BatchApproval.ApprovalType.QUALITY_CHECKER);
    }

    @Test
    void getRemainingApprovals_allReceived_returnsEmpty() {
        when(batchApprovalRepository.findDistinctApprovalTypesByBatchId(batchId))
                .thenReturn(List.of(
                        BatchApproval.ApprovalType.PRODUCTION_HEAD,
                        BatchApproval.ApprovalType.QUALITY_CHECKER));

        List<BatchApproval.ApprovalType> remaining = batchApprovalService.getRemainingApprovals(batchId);

        assertThat(remaining).isEmpty();
    }

    // ==================== Activate Batch ====================

    @Test
    void activateBatch_setsStatusToActive() {
        when(batchRepository.save(any())).thenReturn(batch);
        when(messageQueueService.enqueueJob(any(), any(), any())).thenReturn("job-id");

        batchApprovalService.activateBatch(batch);

        assertThat(batch.getStatus()).isEqualTo(Batch.BatchStatus.ACTIVE);
        verify(batchRepository).save(batch);
    }

    @Test
    void activateBatch_enqueuessBlockchainMintJob() {
        when(batchRepository.save(any())).thenReturn(batch);
        when(messageQueueService.enqueueJob(any(), any(), any())).thenReturn("job-id");

        batchApprovalService.activateBatch(batch);

        verify(messageQueueService).enqueueJob(
                eq(BatchJob.JobType.BLOCKCHAIN_MINT),
                eq(batchId),
                any());
    }
}
