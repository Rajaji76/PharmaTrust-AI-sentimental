package pharmatrust.manufacturing_system.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scan")
@CrossOrigin(origins = "*")
public class ScanController {
    
    @Autowired
    private ScanService scanService;

    @Autowired
    private UnitItemRepository unitItemRepository;

    @Autowired
    private QRCodeService qrCodeService;

    @Autowired
    private ScanLogRepository scanLogRepository;
    
    /**
     * Scan a unit (Public endpoint for patients/consumers)
     */
    @PostMapping
    public ResponseEntity<ScanResponse> scanUnit(@Valid @RequestBody ScanRequest request) {
        ScanResponse response = scanService.scanUnit(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/scan/qr-image/{serialNumber}
     * Returns the QR code PNG image for a given serial number.
     * Used by ManufacturerPanel to display scannable QR codes after batch creation.
     */
    @GetMapping(value = "/qr-image/{serialNumber}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQRImage(@PathVariable String serialNumber) {
        UnitItem unit = unitItemRepository.findBySerialNumber(serialNumber).orElse(null);
        if (unit == null) return ResponseEntity.notFound().build();

        String qrDataUri = unit.getQrPayloadEncrypted();
        if (qrDataUri == null || qrDataUri.isEmpty()) return ResponseEntity.notFound().build();

        // qrPayloadEncrypted stores "data:image/png;base64,..." — extract raw bytes
        try {
            String base64 = qrDataUri.contains(",") 
                ? qrDataUri.split(",", 2)[1] 
                : qrDataUri;
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            return ResponseEntity.ok(imageBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/v1/scan/history - Scan history for authenticated user (used by frontend scanAPI.getScanHistory)
     */
    @GetMapping("/history")
    public ResponseEntity<?> getScanHistory() {
        // Returns last 50 scan logs across all units — frontend uses this for display only
        List<ScanLog> logs = scanLogRepository.findTop50ByOrderByScannedAtDesc();
        List<Map<String, Object>> result = logs.stream().map(l -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("scannedAt", l.getScannedAt().toString());
            entry.put("result", l.getScanResult().name());
            entry.put("serialNumber", l.getUnit() != null ? l.getUnit().getSerialNumber() : "");
            entry.put("anomalyScore", l.getAnomalyScore() != null ? l.getAnomalyScore() : 0.0);
            entry.put("autoFlagged", l.getAutoFlagged() != null && l.getAutoFlagged());
            return entry;
        }).toList();
        return ResponseEntity.ok(result);
    }
}
