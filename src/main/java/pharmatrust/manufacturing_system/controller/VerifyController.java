package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.dto.ScanRequest;
import pharmatrust.manufacturing_system.dto.ScanResponse;
import pharmatrust.manufacturing_system.entity.ScanLog;
import pharmatrust.manufacturing_system.entity.UnitItem;
import pharmatrust.manufacturing_system.repository.ScanLogRepository;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;
import pharmatrust.manufacturing_system.service.QRCodeService;
import pharmatrust.manufacturing_system.service.ScanService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QR Verification and Scanning endpoints
 */
@RestController
@RequestMapping("/api/v1/verify")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
@RequiredArgsConstructor
@Slf4j
public class VerifyController {

    private final ScanService scanService;
    private final QRCodeService qrCodeService;
    private final UnitItemRepository unitItemRepository;
    private final ScanLogRepository scanLogRepository;

    @PostMapping("/scan")
    public ResponseEntity<ScanResponse> verifyScan(@RequestBody ScanRequest request) {
        log.info("Online scan request: serial={}", request.getSerialNumber());
        ScanResponse response = scanService.scanUnit(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/scan/offline")
    public ResponseEntity<Map<String, Object>> verifyOffline(@RequestBody Map<String, String> request) {
        String qrPayload = request.get("qrPayload");
        log.info("Offline scan request received");
        try {
            QRCodeService.VerificationResult result = qrCodeService.verifyQROnline(qrPayload);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("valid", "VALID".equals(result.getStatus()) || result.getStatus().startsWith("VALID"));
            resp.put("status", result.getStatus());
            resp.put("serialNumber", result.getSerialNumber() != null ? result.getSerialNumber() : "");
            resp.put("message", result.getMessage());
            resp.put("offlineMode", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Offline verification failed", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("valid", false);
            err.put("status", "ERROR");
            err.put("message", "Offline verification failed: " + e.getMessage());
            return ResponseEntity.ok(err);
        }
    }

    @GetMapping("/unit/{serial}")
    public ResponseEntity<?> getUnitDetails(@PathVariable String serial) {
        return unitItemRepository.findBySerialNumber(serial)
                .map(unit -> {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("serialNumber", unit.getSerialNumber());
                    resp.put("status", unit.getStatus().name());
                    resp.put("isActive", unit.getIsActive());
                    resp.put("scanCount", unit.getScanCount());
                    resp.put("maxScanLimit", unit.getMaxScanLimit());
                    resp.put("unitType", unit.getUnitType() != null ? unit.getUnitType().name() : "TABLET");
                    resp.put("batchNumber", unit.getBatch().getBatchNumber());
                    resp.put("medicineName", unit.getBatch().getMedicineName());
                    resp.put("expiryDate", unit.getBatch().getExpiryDate().toString());
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/verify/box/{serial} - Get box details with all child units
     */
    @GetMapping("/box/{serial}")
    public ResponseEntity<?> getBoxDetails(@PathVariable String serial) {
        UnitItem box = unitItemRepository.findBySerialNumber(serial).orElse(null);
        if (box == null) return ResponseEntity.notFound().build();

        var batch = box.getBatch();
        var children = unitItemRepository.findByParentUnitId(box.getId());

        var childList = children.stream().map(c -> {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("serialNumber", c.getSerialNumber());
            child.put("unitType", c.getUnitType() != null ? c.getUnitType().name() : "TABLET");
            child.put("status", c.getStatus().name());
            child.put("scanCount", c.getScanCount());
            child.put("maxScanLimit", c.getMaxScanLimit());
            child.put("isActive", c.getIsActive());
            return child;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serialNumber", box.getSerialNumber());
        result.put("unitType", box.getUnitType() != null ? box.getUnitType().name() : "BOX");
        result.put("status", box.getStatus().name());
        result.put("isActive", box.getIsActive());
        result.put("scanCount", box.getScanCount());
        result.put("batchNumber", batch.getBatchNumber());
        result.put("medicineName", batch.getMedicineName());
        result.put("manufacturingDate", batch.getManufacturingDate().toString());
        result.put("expiryDate", batch.getExpiryDate().toString());
        result.put("manufacturerName", batch.getManufacturer() != null ? batch.getManufacturer().getFullName() : "Unknown");
        result.put("batchStatus", batch.getStatus().name());
        result.put("blockchainTxId", batch.getBlockchainTxId() != null ? batch.getBlockchainTxId() : "");
        result.put("merkleRoot", batch.getMerkleRoot() != null ? batch.getMerkleRoot() : "");
        result.put("childCount", children.size());
        result.put("childUnits", childList);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/{serial}")
    public ResponseEntity<?> getScanHistory(@PathVariable String serial) {
        UnitItem unit = unitItemRepository.findBySerialNumber(serial).orElse(null);
        if (unit == null) return ResponseEntity.notFound().build();

        List<ScanLog> logs = scanLogRepository.findByUnitIdOrderByScannedAtDesc(unit.getId());
        List<Map<String, Object>> history = logs.stream().map(l -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("scannedAt", l.getScannedAt().toString());
            entry.put("result", l.getScanResult().name());
            entry.put("location", l.getLocationLat() != null ? l.getLocationLat() + "," + l.getLocationLng() : "unknown");
            entry.put("anomalyScore", l.getAnomalyScore() != null ? l.getAnomalyScore() : 0.0);
            entry.put("autoFlagged", l.getAutoFlagged() != null && l.getAutoFlagged());
            return entry;
        }).toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("serialNumber", serial);
        resp.put("totalScans", logs.size());
        resp.put("history", history);
        return ResponseEntity.ok(resp);
    }
}
