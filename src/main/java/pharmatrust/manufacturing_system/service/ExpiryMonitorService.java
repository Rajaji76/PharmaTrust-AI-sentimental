package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.Alert;
import pharmatrust.manufacturing_system.entity.Batch;
import pharmatrust.manufacturing_system.entity.UnitItem;
import pharmatrust.manufacturing_system.repository.AlertRepository;
import pharmatrust.manufacturing_system.repository.BatchRepository;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * AI Expiry Monitor — runs daily to:
 *  1. Raise EXPIRY_WARNING alert 10 days before expiry
 *  2. Auto-block batch + mark all units EXPIRED on expiry day
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryMonitorService {

    private final BatchRepository batchRepository;
    private final UnitItemRepository unitItemRepository;
    private final AlertRepository alertRepository;

    /**
     * Runs every day at 00:05 AM
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void runDailyExpiryCheck() {
        log.info("[ExpiryMonitor] Running daily expiry check...");
        checkExpiringSoon();
        blockExpiredBatches();
    }

    /**
     * Raise alert for batches expiring within 10 days (only once per batch)
     */
    private void checkExpiringSoon() {
        LocalDate warningCutoff = LocalDate.now().plusDays(10);
        List<Batch> expiringSoon = batchRepository.findBatchesExpiringSoon(warningCutoff);

        for (Batch batch : expiringSoon) {
            long daysLeft = LocalDate.now().until(batch.getExpiryDate()).getDays();
            String msg = String.format(
                "⚠️ EXPIRY WARNING: Batch %s (%s) expires in %d day(s) on %s. Take action immediately.",
                batch.getBatchNumber(), batch.getMedicineName(), daysLeft, batch.getExpiryDate()
            );

            Alert alert = Alert.builder()
                .alertType(Alert.AlertType.EXPIRY_WARNING)
                .severity(daysLeft <= 3 ? Alert.Severity.CRITICAL : Alert.Severity.HIGH)
                .message(msg)
                .entityId(batch.getId())
                .entityType("BATCH")
                .build();

            alertRepository.save(alert);
            batchRepository.markExpiryAlertSent(batch.getId());

            log.warn("[ExpiryMonitor] Alert raised for batch {} — {} days left", batch.getBatchNumber(), daysLeft);
        }
    }

    /**
     * Auto-block batches that have reached or passed their expiry date
     */
    private void blockExpiredBatches() {
        List<Batch> expired = batchRepository.findExpiredBatches(LocalDate.now());

        for (Batch batch : expired) {
            // Block the batch
            batchRepository.updateStatus(batch.getId(), Batch.BatchStatus.RECALLED_AUTO);

            // Mark all units as EXPIRED
            List<UnitItem> units = unitItemRepository.findByBatchId(batch.getId());
            for (UnitItem unit : units) {
                unit.setStatus(UnitItem.UnitStatus.EXPIRED);
                unit.setIsActive(false);
            }
            unitItemRepository.saveAll(units);

            // Create critical alert
            Alert alert = Alert.builder()
                .alertType(Alert.AlertType.EXPIRY_WARNING)
                .severity(Alert.Severity.CRITICAL)
                .message(String.format(
                    "🚨 BATCH EXPIRED & AUTO-BLOCKED: Batch %s (%s) expired on %s. All %d units deactivated.",
                    batch.getBatchNumber(), batch.getMedicineName(), batch.getExpiryDate(), units.size()
                ))
                .entityId(batch.getId())
                .entityType("BATCH")
                .build();

            alertRepository.save(alert);
            log.error("[ExpiryMonitor] Batch {} EXPIRED and auto-blocked. {} units deactivated.",
                batch.getBatchNumber(), units.size());
        }
    }

    /**
     * Manual trigger — call this from a controller or on startup for testing
     */
    @Transactional
    public void runNow() {
        log.info("[ExpiryMonitor] Manual trigger invoked");
        runDailyExpiryCheck();
    }
}
