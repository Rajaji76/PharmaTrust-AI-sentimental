package pharmatrust.manufacturing_system.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pharmatrust.manufacturing_system.dto.*;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;
import pharmatrust.manufacturing_system.service.BatchApprovalService;
import pharmatrust.manufacturing_system.service.BatchService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
@Slf4j
public class BatchController {
    
    @Autowired
    private BatchService batchService;

    @Autowired
    private BatchApprovalService batchApprovalService;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UnitItemRepository unitItemRepository;

    @Autowired
    private RecallEventRepository recallEventRepository;

    @Autowired
    private UserRepository userRepository;
    
    /**
     * Create a new batch with complete production workflow
     * 
     * Includes:
     * - Lab report upload to S3
     * - AI verification (99%+ match)
     * - Bulk unit generation with QR codes
     * - Blockchain token minting
     * 
     * @param medicineName Medicine name
     * @param manufacturingDate Manufacturing date (YYYY-MM-DD)
     * @param expiryDate Expiry date (YYYY-MM-DD)
     * @param totalUnits Total number of units to generate
     * @param labReportFile Lab report PDF file
     * @param testOfficerSignature Test officer's digital signature
     * @param labReportContent Extracted text content from lab report
     * @param authentication Current user authentication
     * @return BatchResponse with blockchain token ID and AI verification score
     */
    @PostMapping(value = "/create-complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MANUFACTURER')")
    public ResponseEntity<?> createBatchWithCompleteWorkflow(
            @RequestParam("medicineName") String medicineName,
            @RequestParam("manufacturingDate") String manufacturingDate,
            @RequestParam("expiryDate") String expiryDate,
            @RequestParam("totalUnits") Integer totalUnits,
            @RequestParam("labReportFile") MultipartFile labReportFile,
            @RequestParam("testOfficerSignature") String testOfficerSignature,
            @RequestParam(value = "labReportContent", required = false, defaultValue = "") String labReportContent,
            Authentication authentication) {
        
        try {
            log.info("Received complete batch creation request for medicine: {}", medicineName);
            
            // Validate file
            if (labReportFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Lab report file is required",
                    "status", "FAILED"
                ));
            }
            
            // Create request DTO
            CreateBatchRequest request = new CreateBatchRequest();
            request.setMedicineName(medicineName);
            request.setManufacturingDate(LocalDate.parse(manufacturingDate));
            request.setExpiryDate(LocalDate.parse(expiryDate));
            request.setTotalUnits(totalUnits);
            
            // If lab report content is empty, use medicine name as mock content
            String reportContent = labReportContent.isEmpty()
                ? ""
                : labReportContent;
            
            // Call complete workflow
            BatchResponse response = batchService.createBatchWithCompleteWorkflow(
                request,
                labReportFile,
                testOfficerSignature,
                reportContent,
                authentication
            );
            
            log.info("Batch created successfully: {}", response.getBatchNumber());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to create batch with complete workflow", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("status", "FAILED");
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Create a new batch (Legacy method - simple workflow)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('MANUFACTURER')")
    public ResponseEntity<BatchResponse> createBatch(
            @Valid @RequestBody CreateBatchRequest request,
            Authentication authentication) {
        BatchResponse response = batchService.createBatch(request, authentication);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all batches for current manufacturer
     */
    @GetMapping("/my-batches")
    @PreAuthorize("hasAuthority('MANUFACTURER')")
    public ResponseEntity<List<BatchResponse>> getMyBatches(Authentication authentication) {
        List<BatchResponse> batches = batchService.getManufacturerBatches(authentication);
        return ResponseEntity.ok(batches);
    }
    
    /**
     * Get batch details with units
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<BatchResponse> getBatchDetails(@PathVariable UUID batchId) {
        BatchResponse response = batchService.getBatchDetails(batchId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/batches/{id}/approve - Approve batch with digital signature
     */
    @PostMapping("/{batchId}/approve")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER','REGULATOR')")
    public ResponseEntity<?> approveBatch(@PathVariable UUID batchId,
                                           @RequestBody Map<String, String> request,
                                           Authentication authentication) {
        try {
            String approvalTypeStr = request.getOrDefault("approvalType", "PRODUCTION_HEAD");
            String digitalSignature = request.getOrDefault("digitalSignature", "");
            BatchApproval.ApprovalType approvalType = BatchApproval.ApprovalType.valueOf(approvalTypeStr);

            Map<String, Object> result = batchApprovalService.approveBatch(
                    batchId, authentication.getName(), approvalType, digitalSignature);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/batches/{id}/quarantine - Quarantine batch
     */
    @PostMapping("/{batchId}/quarantine")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER','REGULATOR')")
    public ResponseEntity<?> quarantineBatch(@PathVariable UUID batchId,
                                              @RequestBody Map<String, String> request) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        batch.setStatus(Batch.BatchStatus.QUARANTINE);
        batchRepository.save(batch);
        log.info("Batch quarantined: {}", batch.getBatchNumber());
        return ResponseEntity.ok(Map.of("success", true, "batchId", batchId, "status", "QUARANTINE"));
    }

    /**
     * POST /api/v1/batches/{id}/recall - Recall batch with reason
     */
    @PostMapping("/{batchId}/recall")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER','REGULATOR')")
    public ResponseEntity<?> recallBatch(@PathVariable UUID batchId,
                                          @RequestBody Map<String, String> request,
                                          Authentication authentication) {
        try {
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new RuntimeException("Batch not found"));

            String reason = request.getOrDefault("reason", "Recalled by manufacturer");
            batch.setStatus(Batch.BatchStatus.RECALLED);
            batchRepository.save(batch);

            // Cascade recall to all units
            List<UnitItem> units = unitItemRepository.findByBatchId(batchId);
            units.forEach(unit -> {
                unit.setStatus(UnitItem.UnitStatus.RECALLED);
                unit.setIsActive(false);
            });
            unitItemRepository.saveAll(units);

            // Create recall event
            User initiator = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            RecallEvent recallEvent = RecallEvent.builder()
                    .batch(batch)
                    .initiatedBy(initiator)
                    .recallType(RecallEvent.RecallType.MANUAL)
                    .reason(reason)
                    .status(RecallEvent.RecallStatus.ACTIVE)
                    .build();
            recallEventRepository.save(recallEvent);

            log.info("Batch recalled: {}, units affected: {}", batch.getBatchNumber(), units.size());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "batchId", batchId,
                    "status", "RECALLED",
                    "unitsAffected", units.size(),
                    "reason", reason
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
