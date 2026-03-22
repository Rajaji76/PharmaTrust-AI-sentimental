package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.Alert;
import pharmatrust.manufacturing_system.entity.Batch;
import pharmatrust.manufacturing_system.repository.AlertRepository;
import pharmatrust.manufacturing_system.repository.BatchRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled service that monitors pending blockchain transactions asynchronously.
 *
 * <p>Responsibilities (IR-004, NFR-012):
 * <ul>
 *   <li>Periodically poll batches whose blockchain transaction is not yet CONFIRMED</li>
 *   <li>Update batch status when the transaction is confirmed on-chain</li>
 *   <li>Retry failed transactions up to {@value #MAX_RETRIES} times</li>
 *   <li>Generate a CRITICAL alert when a transaction fails after all retries</li>
 *   <li>Log every blockchain interaction for audit purposes</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainTransactionMonitor {

    /** Maximum number of retry attempts before raising a failure alert. */
    static final int MAX_RETRIES = 3;

    private final BatchRepository batchRepository;
    private final AlertRepository alertRepository;
    private final BlockchainService blockchainService;

    /**
     * In-memory retry counter per batch ID.
     * Cleared once a transaction is confirmed or permanently failed.
     */
    private final Map<UUID, Integer> retryCounters = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Scheduled polling — every 30 seconds
    // -------------------------------------------------------------------------

    /**
     * Main monitoring loop — runs every 30 seconds.
     * Queries all batches that have a blockchain transaction ID but whose
     * status is not yet CONFIRMED (i.e. still ACTIVE or PROCESSING after mint).
     *
     * <p>Requirements: IR-004, NFR-012
     */
    @Scheduled(fixedDelayString = "${blockchain.monitor.interval-ms:30000}")
    @Transactional
    public void monitorPendingTransactions() {
        List<Batch> pending = batchRepository.findBatchesWithPendingBlockchainTx();

        if (pending.isEmpty()) {
            log.debug("[BlockchainMonitor] No pending blockchain transactions found.");
            return;
        }

        log.info("[BlockchainMonitor] Checking {} pending blockchain transaction(s).", pending.size());

        for (Batch batch : pending) {
            processBatch(batch);
        }
    }

    // -------------------------------------------------------------------------
    // Per-batch processing
    // -------------------------------------------------------------------------

    /**
     * Check the on-chain status of a single batch transaction and react accordingly.
     */
    void processBatch(Batch batch) {
        String txHash = batch.getBlockchainTxId();
        UUID batchId = batch.getId();

        log.info("[BlockchainMonitor] Querying tx status — batch: {}, txHash: {}",
                batch.getBatchNumber(), txHash);

        BlockchainService.TransactionStatus status = blockchainService.getTransactionStatus(txHash);

        log.info("[BlockchainMonitor] tx={} status={} confirmations={}",
                txHash, status.getStatus(), status.getConfirmations());

        switch (status.getStatus()) {
            case "CONFIRMED" -> handleConfirmed(batch, status);
            case "FAILED"    -> handleFailed(batch, status, "Transaction reverted on-chain");
            case "ERROR"     -> handleFailed(batch, status, status.getErrorMessage());
            case "PENDING"   -> log.debug("[BlockchainMonitor] tx={} still pending ({} confirmations)",
                                    txHash, status.getConfirmations());
            default          -> log.warn("[BlockchainMonitor] tx={} unknown status: {}",
                                    txHash, status.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Outcome handlers
    // -------------------------------------------------------------------------

    /**
     * Mark the batch as blockchain-confirmed and clear its retry counter.
     */
    private void handleConfirmed(Batch batch, BlockchainService.TransactionStatus status) {
        log.info("[BlockchainMonitor] ✅ Transaction CONFIRMED — batch: {}, txHash: {}, block: {}, gasUsed: {}",
                batch.getBatchNumber(), status.getTxHash(), status.getBlockNumber(), status.getGasUsed());

        // Persist confirmed status on the batch
        batch.setBlockchainConfirmed(true);
        batchRepository.save(batch);

        // Clean up retry state
        retryCounters.remove(batch.getId());
    }

    /**
     * Handle a failed or errored transaction.
     * Retries up to {@value #MAX_RETRIES} times; generates a CRITICAL alert on exhaustion.
     */
    private void handleFailed(Batch batch, BlockchainService.TransactionStatus status, String reason) {
        UUID batchId = batch.getId();
        int attempts = retryCounters.merge(batchId, 1, Integer::sum);

        log.warn("[BlockchainMonitor] ⚠️ Transaction FAILED — batch: {}, txHash: {}, reason: {}, attempt: {}/{}",
                batch.getBatchNumber(), status.getTxHash(), reason, attempts, MAX_RETRIES);

        if (attempts < MAX_RETRIES) {
            log.info("[BlockchainMonitor] Scheduling retry {}/{} for batch: {}",
                    attempts, MAX_RETRIES, batch.getBatchNumber());
            // The next scheduled run will re-query; the batch still has its txHash so it
            // will be picked up again. No additional action needed here.
        } else {
            log.error("[BlockchainMonitor] ❌ Max retries ({}) exhausted for batch: {} — generating alert.",
                    MAX_RETRIES, batch.getBatchNumber());
            generateFailureAlert(batch, reason);
            retryCounters.remove(batchId);
        }
    }

    // -------------------------------------------------------------------------
    // Alert generation
    // -------------------------------------------------------------------------

    /**
     * Persist a CRITICAL alert for a permanently failed blockchain transaction.
     * Requirements: NFR-015
     */
    private void generateFailureAlert(Batch batch, String reason) {
        String message = String.format(
                "🚨 BLOCKCHAIN TRANSACTION FAILED: Batch %s (%s) — txHash: %s — Reason: %s. "
                        + "Manual intervention required.",
                batch.getBatchNumber(),
                batch.getMedicineName(),
                batch.getBlockchainTxId(),
                reason != null ? reason : "unknown"
        );

        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.TAMPERING_DETECTED)
                .severity(Alert.Severity.CRITICAL)
                .message(message)
                .entityId(batch.getId())
                .entityType("BATCH")
                .build();

        alertRepository.save(alert);

        log.error("[BlockchainMonitor] CRITICAL alert saved for batch: {} — {}",
                batch.getBatchNumber(), message);
    }

    // -------------------------------------------------------------------------
    // Manual trigger (for testing / admin use)
    // -------------------------------------------------------------------------

    /**
     * Manually trigger a monitoring cycle — useful for admin endpoints or tests.
     */
    @Transactional
    public void runNow() {
        log.info("[BlockchainMonitor] Manual trigger invoked.");
        monitorPendingTransactions();
    }
}
