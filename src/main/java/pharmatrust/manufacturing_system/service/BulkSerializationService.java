package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pharmatrust.manufacturing_system.entity.Batch;
import pharmatrust.manufacturing_system.entity.UnitItem;
import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;
import pharmatrust.manufacturing_system.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Bulk Serialization Service
 * 
 * Features:
 * - Fast generation of 1000+ units with unique serial numbers
 * - Batch insert optimization (100 units per batch)
 * - Parallel processing for QR code generation
 * - Hierarchical packaging support (10 doses per box)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkSerializationService {

    private final UnitItemRepository unitItemRepository;
    private final UserRepository userRepository;
    private final CryptographyService cryptographyService;
    private final QRCodeService qrCodeService;

    private static final int BATCH_INSERT_SIZE = 100;
    private static final int DOSES_PER_BOX = 10;
    private static final String SHARED_SECRET = "pharmatrust-totp-secret";

    /**
     * Generate units in bulk with optimized batch inserts
     * 
     * Hierarchy:
     * - Individual doses (TABLET) with QR codes
     * - Boxes (BOX) containing 10 doses with aggregated QR codes
     * 
     * @param batch Batch entity
     * @param totalUnits Total number of individual units to generate
     * @param manufacturerId UUID of the manufacturer (avoids Hibernate stale object issues)
     * @return List of generated units (both doses and boxes)
     */
    public List<UnitItem> generateUnitsInBulk(Batch batch, Integer totalUnits, UUID manufacturerId) {
        log.info("Starting bulk serialization for batch: {} with {} units", batch.getBatchNumber(), totalUnits);
        long startTime = System.currentTimeMillis();

        // Use getReferenceById to get a proxy — no extra DB hit, avoids stale object issues
        User manufacturerRef = userRepository.getReferenceById(manufacturerId);

        List<UnitItem> allUnits = new ArrayList<>();

        // Step 1: Generate individual doses (TABLET)
        List<UnitItem> doses = generateIndividualDoses(batch, totalUnits, manufacturerRef);
        allUnits.addAll(doses);

        // Step 2: Save doses in batches for performance
        saveDosesInBatches(doses);

        // Step 3: Generate boxes (10 doses per box)
        List<UnitItem> boxes = generateBoxes(batch, doses, manufacturerRef);
        allUnits.addAll(boxes);

        // Step 4: Save boxes
        if (!boxes.isEmpty()) {
            unitItemRepository.saveAll(boxes);
            log.info("Saved {} boxes to database", boxes.size());
        }

        long endTime = System.currentTimeMillis();
        log.info("Bulk serialization completed in {} ms", (endTime - startTime));

        return allUnits;
    }

    /**
     * Generate individual doses with serial numbers and digital signatures
     */
    private List<UnitItem> generateIndividualDoses(Batch batch, Integer totalUnits, User manufacturer) {
        List<UnitItem> doses = new ArrayList<>();

        for (int i = 0; i < totalUnits; i++) {
            String serialNumber = generateSerialNumber(batch.getBatchNumber(), i);

            // Generate digital signature for unit
            String unitData = serialNumber + batch.getBatchNumber() + batch.getMedicineName();
            String digitalSignature = cryptographyService.generateHMAC(unitData, "pharmatrust-secret-key");

            // Generate QR code for individual dose
            String qrCode = qrCodeService.generateUnitQRCode(
                serialNumber,
                batch.getBatchNumber(),
                batch.getMedicineName(),
                batch.getManufacturingDate().toString(),
                batch.getExpiryDate().toString(),
                digitalSignature,
                SHARED_SECRET
            );

            UnitItem dose = UnitItem.builder()
                .serialNumber(serialNumber)
                .batch(batch)
                .unitType(UnitItem.UnitType.TABLET)
                .digitalSignature(digitalSignature)
                .qrPayloadEncrypted(qrCode)
                .status(UnitItem.UnitStatus.ACTIVE)
                .scanCount(0)
                .maxScanLimit(5)
                .isActive(true)
                .currentOwner(manufacturer)
                .build();

            doses.add(dose);
        }

        log.info("Generated {} individual doses", doses.size());
        return doses;
    }

    /**
     * Save doses in batches for optimized database performance
     */
    private void saveDosesInBatches(List<UnitItem> doses) {
        int totalBatches = (int) Math.ceil((double) doses.size() / BATCH_INSERT_SIZE);

        for (int i = 0; i < totalBatches; i++) {
            int start = i * BATCH_INSERT_SIZE;
            int end = Math.min(start + BATCH_INSERT_SIZE, doses.size());
            List<UnitItem> batchToSave = doses.subList(start, end);

            unitItemRepository.saveAll(batchToSave);
            log.info("Saved batch {}/{}: {} units", (i + 1), totalBatches, batchToSave.size());
        }
    }

    /**
     * Generate boxes containing 10 doses each
     * Each box has a parent QR code with aggregated data
     */
    private List<UnitItem> generateBoxes(Batch batch, List<UnitItem> doses, User manufacturer) {
        List<UnitItem> boxes = new ArrayList<>();
        int totalBoxes = (int) Math.ceil((double) doses.size() / DOSES_PER_BOX);

        for (int boxIndex = 0; boxIndex < totalBoxes; boxIndex++) {
            int start = boxIndex * DOSES_PER_BOX;
            int end = Math.min(start + DOSES_PER_BOX, doses.size());
            List<UnitItem> dosesInBox = doses.subList(start, end);

            // Generate box serial number
            String boxSerialNumber = generateBoxSerialNumber(batch.getBatchNumber(), boxIndex);

            // Calculate Merkle root of doses in this box
            List<String> doseSignatures = dosesInBox.stream()
                .map(UnitItem::getDigitalSignature)
                .collect(Collectors.toList());
            String boxMerkleRoot = cryptographyService.calculateMerkleRoot(doseSignatures);

            // Generate box digital signature
            String boxData = boxSerialNumber + batch.getBatchNumber() + dosesInBox.size();
            String boxSignature = cryptographyService.generateHMAC(boxData, "pharmatrust-secret-key");

            // Generate parent QR code for box
            String boxQrCode = qrCodeService.generateParentQRCode(
                boxSerialNumber,
                dosesInBox.size(),
                boxMerkleRoot,
                batch.getBatchNumber()
            );

            UnitItem box = UnitItem.builder()
                .serialNumber(boxSerialNumber)
                .batch(batch)
                .unitType(UnitItem.UnitType.BOX)
                .digitalSignature(boxSignature)
                .qrPayloadEncrypted(boxQrCode)
                .status(UnitItem.UnitStatus.ACTIVE)
                .scanCount(0)
                .maxScanLimit(10)
                .isActive(true)
                .currentOwner(manufacturer)
                .build();

            boxes.add(box);

            // Link doses to this box (set parent)
            for (UnitItem dose : dosesInBox) {
                dose.setParentUnit(box);
            }
        }

        log.info("Generated {} boxes for {} doses", boxes.size(), doses.size());
        return boxes;
    }

    /**
     * Generate deterministic serial number for individual dose
     */
    private String generateSerialNumber(String batchNumber, int index) {
        String indexStr = String.format("%06d", index);
        String data = batchNumber + indexStr;
        String hash = cryptographyService.hashSHA256(data).substring(0, 8).toUpperCase();
        return batchNumber + "-D-" + indexStr + "-" + hash;
    }

    /**
     * Generate serial number for box
     */
    private String generateBoxSerialNumber(String batchNumber, int boxIndex) {
        String indexStr = String.format("%04d", boxIndex);
        String data = batchNumber + "BOX" + indexStr;
        String hash = cryptographyService.hashSHA256(data).substring(0, 8).toUpperCase();
        return batchNumber + "-BOX-" + indexStr + "-" + hash;
    }

    /**
     * Generate QR codes in parallel for better performance
     * (Optional optimization for very large batches)
     */
    public CompletableFuture<Void> generateQRCodesAsync(List<UnitItem> units, Batch batch) {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<CompletableFuture<Void>> futures = units.stream()
            .map(unit -> CompletableFuture.runAsync(() -> {
                if (unit.getUnitType() == UnitItem.UnitType.TABLET) {
                    String qrCode = qrCodeService.generateUnitQRCode(
                        unit.getSerialNumber(),
                        batch.getBatchNumber(),
                        batch.getMedicineName(),
                        batch.getManufacturingDate().toString(),
                        batch.getExpiryDate().toString(),
                        unit.getDigitalSignature(),
                        SHARED_SECRET
                    );
                    unit.setQrPayloadEncrypted(qrCode);
                }
            }, executor))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((result, ex) -> executor.shutdown());
    }
}
