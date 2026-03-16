package pharmatrust.manufacturing_system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pharmatrust.manufacturing_system.dto.*;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BatchService {
    
    @Autowired
    private BatchRepository batchRepository;
    
    @Autowired
    private UnitItemRepository unitItemRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private CryptographyService cryptographyService;
    
    @Autowired
    private LabReportService labReportService;
    
    @Autowired
    private AIVerificationService aiVerificationService;
    
    @Autowired
    private BlockchainTokenService blockchainTokenService;
    
    @Autowired
    private BulkSerializationService bulkSerializationService;
    
    /**
     * Create a new batch with complete production workflow
     * 
     * Workflow:
     * 1. Upload lab report to S3 → Calculate SHA-256 hash
     * 2. AI verification (99%+ match with historical data)
     * 3. Verify test officer digital signature
     * 4. Generate bulk units (1000 doses + boxes with QR codes)
     * 5. Calculate Merkle root of all units
     * 6. Mint blockchain token (1 batch = 1 token)
     * 7. Activate batch
     */
    @Transactional
    public BatchResponse createBatchWithCompleteWorkflow(
            CreateBatchRequest request,
            MultipartFile labReportFile,
            String testOfficerSignature,
            String labReportContent,
            Authentication authentication
    ) {
        log.info("Starting complete batch creation workflow for medicine: {}", request.getMedicineName());
        
        // Get current user (manufacturer)
        String email = authentication.getName();
        User manufacturer = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Validate manufacturer role
        if (manufacturer.getRole() != User.Role.MANUFACTURER) {
            throw new RuntimeException("Only manufacturers can create batches");
        }
        
        // Generate batch number
        String batchNumber = generateBatchNumber(request.getMedicineName());
        log.info("Generated batch number: {}", batchNumber);
        
        // STEP 1: Upload lab report to S3 and calculate hash
        LabReportService.LabReportUploadResult labReportResult = labReportService.uploadLabReport(
            labReportFile,
            batchNumber,
            testOfficerSignature
        );
        
        if (!labReportResult.isSuccess()) {
            throw new RuntimeException("Lab report upload failed");
        }
        log.info("Lab report uploaded: S3 key={}, hash={}", labReportResult.getS3Key(), labReportResult.getFileHash());
        
        // STEP 2: AI verification
        // Use PDF-extracted text if available; fall back to manually passed content
        // (Word/image PDFs sometimes produce empty extraction)
        String textForVerification = (labReportResult.getExtractedText() != null
                && labReportResult.getExtractedText().trim().length() > 50)
                ? labReportResult.getExtractedText()
                : labReportContent;
        log.info("[AI] Using {} chars for verification (source: {})",
                textForVerification.length(),
                labReportResult.getExtractedText().trim().length() > 50 ? "PDF extraction" : "manual input");

        AIVerificationService.AIVerificationResult aiResult = aiVerificationService.verifyLabReport(
            request.getMedicineName(),
            labReportResult.getFileHash(),
            textForVerification
        );
        
        if (!aiResult.isPassed()) {
            throw new RuntimeException("AI verification failed: " + aiResult.getReason());
        }
        log.info("AI verification passed: score={}%, confidence={}%", 
            aiResult.getSimilarityScore(), aiResult.getConfidence());
        
        // STEP 3: Create batch entity
        Batch batch = Batch.builder()
            .batchNumber(batchNumber)
            .medicineName(request.getMedicineName())
            .manufacturingDate(request.getManufacturingDate())
            .expiryDate(request.getExpiryDate())
            .manufacturer(manufacturer)
            .labReportHash(labReportResult.getFileHash())
            .labReportS3Key(labReportResult.getS3Key())
            .totalUnits(request.getTotalUnits())
            .status(Batch.BatchStatus.PROCESSING)
            .build();
        
        // Generate digital signature for batch
        String batchData = batchNumber + request.getMedicineName() + 
                          request.getManufacturingDate() + request.getExpiryDate();
        String batchSignature = cryptographyService.generateHMAC(batchData, "pharmatrust-secret-key");
        batch.setDigitalSignature(batchSignature);
        
        // Save batch
        batch = batchRepository.save(batch);
        log.info("Batch created in database: {}", batch.getId());
        
        // STEP 4: Generate units in bulk with QR codes (doses + boxes)
        // Pass manufacturerId (UUID) instead of User object to avoid Hibernate StaleObjectStateException
        // After saving 1000+ UnitItems, the manufacturer User entity becomes stale in the session
        List<UnitItem> allUnits = bulkSerializationService.generateUnitsInBulk(
            batch,
            request.getTotalUnits(),
            manufacturer.getId()
        );
        
        // Filter only individual doses for Merkle root calculation
        List<UnitItem> doses = allUnits.stream()
            .filter(unit -> unit.getUnitType() == UnitItem.UnitType.TABLET)
            .collect(Collectors.toList());
        
        // STEP 5: Calculate Merkle root from all dose signatures
        List<String> signatures = doses.stream()
            .map(UnitItem::getDigitalSignature)
            .collect(Collectors.toList());
        String merkleRoot = cryptographyService.calculateMerkleRoot(signatures);
        batch.setMerkleRoot(merkleRoot);
        log.info("Merkle root calculated: {}", merkleRoot);
        
        // STEP 6: Mint blockchain token (1 batch = 1 token)
        BlockchainTokenService.BlockchainTokenResult tokenResult = blockchainTokenService.mintBatchToken(
            batchNumber,
            request.getMedicineName(),
            request.getManufacturingDate(),
            request.getExpiryDate(),
            labReportResult.getFileHash(),
            testOfficerSignature,
            merkleRoot
        );
        
        if (!tokenResult.isSuccess()) {
            throw new RuntimeException("Blockchain token minting failed: " + tokenResult.getErrorMessage());
        }
        
        batch.setBlockchainTxId(tokenResult.getTransactionId());
        log.info("Blockchain token minted: txId={}, tokenId={}", 
            tokenResult.getTransactionId(), tokenResult.getTokenId());
        
        // STEP 7: Update batch status to ACTIVE
        batch.setStatus(Batch.BatchStatus.ACTIVE);
        batch = batchRepository.save(batch);
        log.info("Batch activated successfully");
        
        // Prepare response with units
        BatchResponse response = BatchResponse.fromEntity(batch);
        response.setBlockchainTokenId(tokenResult.getTokenId());
        response.setAiVerificationScore(aiResult.getSimilarityScore());
        
        List<UnitItemResponse> unitResponses = allUnits.stream()
            .map(unit -> {
                UnitItemResponse unitResponse = UnitItemResponse.fromEntity(unit);
                unitResponse.setQrCodeData(unit.getQrPayloadEncrypted());
                return unitResponse;
            })
            .collect(Collectors.toList());
        response.setUnits(unitResponses);
        
        log.info("Complete batch creation workflow finished successfully");
        return response;
    }
    
    /**
     * Create a new batch with units and digital signatures (Legacy method)
     */
    @Transactional
    public BatchResponse createBatch(CreateBatchRequest request, Authentication authentication) {
        // Get current user (manufacturer)
        String email = authentication.getName();
        User manufacturer = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Validate manufacturer role
        if (manufacturer.getRole() != User.Role.MANUFACTURER) {
            throw new RuntimeException("Only manufacturers can create batches");
        }
        
        // Generate batch number
        String batchNumber = generateBatchNumber(request.getMedicineName());
        
        // Create batch entity
        Batch batch = Batch.builder()
            .batchNumber(batchNumber)
            .medicineName(request.getMedicineName())
            .manufacturingDate(request.getManufacturingDate())
            .expiryDate(request.getExpiryDate())
            .manufacturer(manufacturer)
            .labReportHash(request.getLabReportHash())
            .totalUnits(request.getTotalUnits())
            .status(Batch.BatchStatus.PROCESSING)
            .build();
        
        // Generate digital signature for batch
        String batchData = batchNumber + request.getMedicineName() + 
                          request.getManufacturingDate() + request.getExpiryDate();
        String batchSignature = cryptographyService.generateHMAC(batchData, "pharmatrust-secret-key");
        batch.setDigitalSignature(batchSignature);
        
        // Save batch
        batch = batchRepository.save(batch);
        
        // Generate units with digital signatures
        // Reload manufacturer reference to avoid stale entity after batch save
        final UUID manufacturerId = manufacturer.getId();
        List<UnitItem> units = generateUnits(batch, request.getTotalUnits(), manufacturerId);
        unitItemRepository.saveAll(units);
        
        // Calculate Merkle root from all unit signatures
        List<String> signatures = units.stream()
            .map(UnitItem::getDigitalSignature)
            .collect(Collectors.toList());
        String merkleRoot = cryptographyService.calculateMerkleRoot(signatures);
        batch.setMerkleRoot(merkleRoot);
        
        // Update batch status
        batch.setStatus(Batch.BatchStatus.ACTIVE);
        batch = batchRepository.save(batch);
        
        // Prepare response with units
        BatchResponse response = BatchResponse.fromEntity(batch);
        List<UnitItemResponse> unitResponses = units.stream()
            .map(UnitItemResponse::fromEntity)
            .collect(Collectors.toList());
        response.setUnits(unitResponses);
        
        return response;
    }
    
    /**
     * Generate units for a batch with unique serial numbers and signatures
     */
    private List<UnitItem> generateUnits(Batch batch, Integer totalUnits, UUID manufacturerId) {
        List<UnitItem> units = new ArrayList<>();
        // Use proxy reference to avoid stale object issues
        User manufacturerRef = userRepository.findById(manufacturerId)
            .orElseThrow(() -> new RuntimeException("Manufacturer not found"));
        
        for (int i = 0; i < totalUnits; i++) {
            String serialNumber = generateSerialNumber(batch.getBatchNumber(), i);
            
            // Generate digital signature for unit
            String unitData = serialNumber + batch.getBatchNumber() + batch.getMedicineName();
            String digitalSignature = cryptographyService.generateHMAC(unitData, "pharmatrust-secret-key");
            
            UnitItem unit = UnitItem.builder()
                .serialNumber(serialNumber)
                .batch(batch)
                .unitType(UnitItem.UnitType.TABLET)
                .digitalSignature(digitalSignature)
                .status(UnitItem.UnitStatus.ACTIVE)
                .scanCount(0)
                .maxScanLimit(5)
                .isActive(true)
                .currentOwner(manufacturerRef)
                .build();
            
            units.add(unit);
        }
        
        return units;
    }
    
    /**
     * Generate unique batch number
     */
    private String generateBatchNumber(String medicineName) {
        String prefix = medicineName.substring(0, Math.min(3, medicineName.length())).toUpperCase();
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return prefix + "-" + timestamp + "-" + random;
    }
    
    /**
     * Generate deterministic serial number for unit
     */
    private String generateSerialNumber(String batchNumber, int index) {
        String indexStr = String.format("%06d", index);
        String data = batchNumber + indexStr;
        String hash = cryptographyService.hashSHA256(data).substring(0, 8).toUpperCase();
        return batchNumber + "-" + indexStr + "-" + hash;
    }
    
    /**
     * Get all batches for current manufacturer
     */
    public List<BatchResponse> getManufacturerBatches(Authentication authentication) {
        String email = authentication.getName();
        User manufacturer = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Batch> batches = batchRepository.findByManufacturer(manufacturer);
        return batches.stream()
            .map(BatchResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Get batch details with units
     */
    public BatchResponse getBatchDetails(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new RuntimeException("Batch not found"));
        
        List<UnitItem> units = unitItemRepository.findByBatch(batch);
        
        BatchResponse response = BatchResponse.fromEntity(batch);
        List<UnitItemResponse> unitResponses = units.stream()
            .map(unit -> {
                UnitItemResponse unitResponse = UnitItemResponse.fromEntity(unit);
                // Add QR code data (serial number for now)
                unitResponse.setQrCodeData(unit.getSerialNumber());
                return unitResponse;
            })
            .collect(Collectors.toList());
        response.setUnits(unitResponses);
        
        return response;
    }
}
