package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RabbitMQ Worker for async unit generation, QR generation, and blockchain minting
 * Implements idempotent processing with crash-safety
 * Requirements: FR-008, FR-009, NFR-002
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UnitGenerationWorker {

    private final BatchRepository batchRepository;
    private final UserRepository userRepository;
    private final BulkSerializationService bulkSerializationService;
    private final CryptographyService cryptographyService;
    private final BlockchainTokenService blockchainTokenService;
    private final MessageQueueService messageQueueService;

    /**
     * Process unit generation jobs from RabbitMQ
     * Idempotent: uses ON CONFLICT DO NOTHING via unique serial_number constraint
     */
    @RabbitListener(queues = "${queue.unit-generation:unit-generation-queue}", autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:false}")
    @Transactional
    public void processUnitGenerationJob(Map<String, Object> message) {
        String jobId = (String) message.get("jobId");
        String batchId = (String) message.get("batchId");
        Integer quantity = message.containsKey("quantity") ? (Integer) message.get("quantity") : 0;

        log.info("Processing unit generation job: jobId={}, batchId={}, quantity={}", jobId, batchId, quantity);

        UUID jobUUID = UUID.fromString(jobId);

        try {
            messageQueueService.updateJobProgress(jobUUID, 0);

            Batch batch = batchRepository.findById(UUID.fromString(batchId))
                    .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

            User manufacturer = batch.getManufacturer();

            // Generate units in bulk (idempotent - duplicate serial numbers are ignored)
            List<UnitItem> allUnits = bulkSerializationService.generateUnitsInBulk(
                    batch, quantity > 0 ? quantity : batch.getTotalUnits(), manufacturer.getId());

            // Calculate Merkle root
            List<String> signatures = allUnits.stream()
                    .filter(u -> u.getUnitType() == UnitItem.UnitType.TABLET)
                    .map(UnitItem::getDigitalSignature)
                    .collect(Collectors.toList());

            String merkleRoot = cryptographyService.calculateMerkleRoot(signatures);
            batch.setMerkleRoot(merkleRoot);
            batch.setStatus(Batch.BatchStatus.PENDING_APPROVAL);
            batchRepository.save(batch);

            messageQueueService.updateJobProgress(jobUUID, allUnits.size());
            messageQueueService.completeJob(jobUUID);

            log.info("Unit generation completed: {} units, merkleRoot={}", allUnits.size(), merkleRoot);

        } catch (Exception e) {
            log.error("Unit generation job failed: jobId={}", jobId, e);
            messageQueueService.failJob(jobUUID, e.getMessage());
            throw e; // Re-throw to trigger DLQ
        }
    }

    /**
     * Process QR generation jobs
     */
    @RabbitListener(queues = "${queue.qr-generation:qr-generation-queue}", autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:false}")
    @Transactional
    public void processQRGenerationJob(Map<String, Object> message) {
        String jobId = (String) message.get("jobId");
        String batchId = (String) message.get("batchId");

        log.info("Processing QR generation job: jobId={}, batchId={}", jobId, batchId);
        UUID jobUUID = UUID.fromString(jobId);

        try {
            messageQueueService.updateJobProgress(jobUUID, 0);
            // QR codes are generated inline during unit generation
            // This queue handles re-generation if needed
            messageQueueService.completeJob(jobUUID);
            log.info("QR generation job completed: {}", jobId);
        } catch (Exception e) {
            log.error("QR generation job failed: {}", jobId, e);
            messageQueueService.failJob(jobUUID, e.getMessage());
            throw e;
        }
    }

    /**
     * Process blockchain minting jobs
     */
    @RabbitListener(queues = "${queue.blockchain-mint:blockchain-mint-queue}", autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:false}")
    @Transactional
    public void processBlockchainMintJob(Map<String, Object> message) {
        String jobId = (String) message.get("jobId");
        String batchId = (String) message.get("batchId");

        log.info("Processing blockchain mint job: jobId={}, batchId={}", jobId, batchId);
        UUID jobUUID = UUID.fromString(jobId);

        try {
            messageQueueService.updateJobProgress(jobUUID, 0);

            Batch batch = batchRepository.findById(UUID.fromString(batchId))
                    .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

            BlockchainTokenService.BlockchainTokenResult result = blockchainTokenService.mintBatchToken(
                    batch.getBatchNumber(),
                    batch.getMedicineName(),
                    batch.getManufacturingDate(),
                    batch.getExpiryDate(),
                    batch.getLabReportHash(),
                    batch.getDigitalSignature(),
                    batch.getMerkleRoot()
            );

            if (result.isSuccess()) {
                batch.setBlockchainTxId(result.getTransactionId());
                batch.setStatus(Batch.BatchStatus.ACTIVE);
                batchRepository.save(batch);
                messageQueueService.completeJob(jobUUID);
                log.info("Blockchain mint completed: txId={}", result.getTransactionId());
            } else {
                throw new RuntimeException("Blockchain mint failed: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Blockchain mint job failed: {}", jobId, e);
            messageQueueService.failJob(jobUUID, e.getMessage());
            throw e;
        }
    }
}
