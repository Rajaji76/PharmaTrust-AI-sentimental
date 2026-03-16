package pharmatrust.manufacturing_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.dto.*;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.time.Instant;
import java.util.List;

@Service
public class ScanService {
    
    @Autowired
    private UnitItemRepository unitItemRepository;
    
    @Autowired
    private ScanLogRepository scanLogRepository;
    
    @Autowired
    private AlertRepository alertRepository;
    
    /**
     * Scan a unit and check for counterfeits
     * Returns complete batch details for transparency
     */
    @Transactional
    public ScanResponse scanUnit(ScanRequest request) {
        // Find unit by serial number
        UnitItem unit = unitItemRepository.findBySerialNumber(request.getSerialNumber())
            .orElse(null);
        
        if (unit == null) {
            return createInvalidScanResponse("INVALID SERIAL NUMBER - Unit not found in database");
        }
        
        // Get batch details
        Batch batch = unit.getBatch();
        
        // Check if batch is recalled or blocked
        boolean isRecalled = batch.getStatus() == Batch.BatchStatus.RECALLED || 
                            batch.getStatus() == Batch.BatchStatus.RECALLED_AUTO;
        boolean isBlocked = !unit.getIsActive() || 
                           unit.getStatus() == UnitItem.UnitStatus.RECALLED ||
                           unit.getStatus() == UnitItem.UnitStatus.RECALLED_AUTO;
        
        // If recalled or blocked, return DANGER alert immediately
        if (isRecalled || isBlocked) {
            return createRecalledScanResponse(unit, batch, "DANGER: This batch has been recalled due to safety concerns. DO NOT CONSUME!");
        }
        
        // Increment scan count
        unit.setScanCount(unit.getScanCount() + 1);
        unit.setLastScannedAt(Instant.now());
        
        if (unit.getFirstScannedAt() == null) {
            unit.setFirstScannedAt(Instant.now());
        }
        
        // Check if scan limit exceeded
        boolean isCounterfeit = unit.getScanCount() > unit.getMaxScanLimit();
        
        ScanLog.ScanResult scanResult;
        String message;
        
        if (isCounterfeit) {
            scanResult = ScanLog.ScanResult.FLAGGED;
            message = "⚠️ STOLEN OR COUNTERFEIT - Scan limit exceeded!";
            unit.setIsActive(false);
            
            // Create alert
            createCounterfeitAlert(unit);
        } else if (unit.getScanCount() >= unit.getMaxScanLimit() - 1) {
            scanResult = ScanLog.ScanResult.SUSPICIOUS;
            message = "⚠️ WARNING - Approaching scan limit (" + unit.getScanCount() + "/" + unit.getMaxScanLimit() + ")";
        } else {
            scanResult = ScanLog.ScanResult.VALID;
            message = "✓ AUTHENTIC - Medicine verified (Scan " + unit.getScanCount() + "/" + unit.getMaxScanLimit() + ")";
        }
        
        // Save unit
        unitItemRepository.save(unit);
        
        // Create scan log
        ScanLog scanLog = ScanLog.builder()
            .unit(unit)
            .scannedAt(Instant.now())
            .locationLat(request.getLocationLat())
            .locationLng(request.getLocationLng())
            .deviceInfo(request.getDeviceInfo())
            .deviceFingerprint(request.getDeviceFingerprint())
            .scanResult(scanResult)
            .anomalyScore(isCounterfeit ? 1.0f : 0.0f)
            .autoFlagged(isCounterfeit)
            .build();
        
        scanLogRepository.save(scanLog);
        
        // Prepare response with complete batch details
        ScanResponse response = new ScanResponse();
        response.setIsValid(!isCounterfeit);
        response.setResult(scanResult.name());
        response.setMessage(message);
        response.setScanCount(unit.getScanCount());
        response.setMaxScanLimit(unit.getMaxScanLimit());
        response.setIsCounterfeit(isCounterfeit);
        response.setIsRecalled(false);
        response.setIsBlocked(false);
        response.setAnomalyScore(scanLog.getAnomalyScore());
        response.setUnitDetails(UnitItemResponse.fromEntity(unit));
        
        // Add complete batch details for transparency
        response.setBatchNumber(batch.getBatchNumber());
        response.setMedicineName(batch.getMedicineName());
        response.setManufacturingDate(batch.getManufacturingDate());
        response.setExpiryDate(batch.getExpiryDate());
        response.setManufacturerName(batch.getManufacturer() != null ? batch.getManufacturer().getFullName() : "Unknown");
        response.setBatchStatus(batch.getStatus().name());
        response.setBlockchainTxId(batch.getBlockchainTxId());
        response.setMerkleRoot(batch.getMerkleRoot());
        
        // Populate hierarchy info
        response.setUnitType(unit.getUnitType() != null ? unit.getUnitType().name() : "TABLET");
        if (unit.getParentUnit() != null) {
            response.setParentSerialNumber(unit.getParentUnit().getSerialNumber());
        }
        // If this is a BOX or CARTON, include child units
        if (unit.getUnitType() == UnitItem.UnitType.BOX || unit.getUnitType() == UnitItem.UnitType.CARTON) {
            List<UnitItem> children = unitItemRepository.findByParentUnitId(unit.getId());
            response.setChildCount(children.size());
            response.setChildUnits(children.stream().map(c -> new ScanResponse.ChildUnitInfo(
                c.getSerialNumber(),
                c.getUnitType() != null ? c.getUnitType().name() : "TABLET",
                c.getStatus().name(),
                c.getScanCount()
            )).toList());
        }
        
        return response;
    }
    
    /**
     * Create recalled/blocked scan response with DANGER alert
     */
    private ScanResponse createRecalledScanResponse(UnitItem unit, Batch batch, String dangerMessage) {
        ScanResponse response = new ScanResponse();
        response.setIsValid(false);
        response.setResult("RECALLED");
        response.setMessage(dangerMessage);
        response.setIsCounterfeit(false);
        response.setIsRecalled(true);
        response.setIsBlocked(true);
        response.setRecallReason("Batch has been recalled by manufacturer or regulator due to safety concerns");
        response.setScanCount(unit.getScanCount());
        response.setMaxScanLimit(unit.getMaxScanLimit());
        response.setAnomalyScore(1.0f);
        response.setUnitDetails(UnitItemResponse.fromEntity(unit));
        
        // Add complete batch details
        response.setBatchNumber(batch.getBatchNumber());
        response.setMedicineName(batch.getMedicineName());
        response.setManufacturingDate(batch.getManufacturingDate());
        response.setExpiryDate(batch.getExpiryDate());
        response.setManufacturerName(batch.getManufacturer() != null ? batch.getManufacturer().getFullName() : "Unknown");
        response.setBatchStatus(batch.getStatus().name());
        response.setBlockchainTxId(batch.getBlockchainTxId());
        response.setMerkleRoot(batch.getMerkleRoot());
        
        return response;
    }
    
    /**
     * Create invalid scan response
     */
    private ScanResponse createInvalidScanResponse(String message) {
        ScanResponse response = new ScanResponse();
        response.setIsValid(false);
        response.setResult("INVALID");
        response.setMessage(message);
        response.setIsCounterfeit(true);
        return response;
    }
    
    /**
     * Create counterfeit alert
     */
    private void createCounterfeitAlert(UnitItem unit) {
        Alert alert = Alert.builder()
            .alertType(Alert.AlertType.TAMPERING_DETECTED)
            .severity(Alert.Severity.CRITICAL)
            .message("Unit " + unit.getSerialNumber() + " has exceeded scan limit. Possible counterfeit or stolen medicine.")
            .entityId(unit.getId())
            .entityType("UNIT_ITEM")
            .build();
        
        alertRepository.save(alert);
    }
}
