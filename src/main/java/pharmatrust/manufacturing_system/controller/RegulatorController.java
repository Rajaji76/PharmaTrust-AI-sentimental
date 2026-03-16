package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;
import pharmatrust.manufacturing_system.service.HierarchyKillSwitchService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regulator endpoints with privacy-protected RBAC
 * Regulators can ONLY see: alerts, recalls, flagged items, audit logs
 * Regulators CANNOT see: production volumes, supply chain routes, business metrics
 * Requirements: FR-003, NFR-009
 */
@RestController
@RequestMapping("/api/v1/regulator")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('REGULATOR')")
public class RegulatorController {

    private final AlertRepository alertRepository;
    private final RecallEventRepository recallEventRepository;
    private final BatchRepository batchRepository;
    private final AuditLogRepository auditLogRepository;
    private final HierarchyKillSwitchService hierarchyKillSwitchService;
    private final UnitItemRepository unitItemRepository;
    private final ScanLogRepository scanLogRepository;

    /**
     * GET /api/v1/regulator/alerts - View fraud alerts only
     */
    @GetMapping("/alerts")
    public ResponseEntity<?> getFraudAlerts() {
        List<Alert> alerts = alertRepository.findFraudAlerts();
        log.info("Regulator accessed fraud alerts: {} records", alerts.size());
        return ResponseEntity.ok(Map.of(
                "totalAlerts", alerts.size(),
                "unacknowledged", alerts.stream().filter(a -> !a.getAcknowledged()).count(),
                "alerts", alerts.stream().map(a -> Map.of(
                        "id", a.getId(),
                        "type", a.getAlertType().name(),
                        "severity", a.getSeverity().name(),
                        "message", a.getMessage(),
                        "createdAt", a.getCreatedAt().toString(),
                        "acknowledged", a.getAcknowledged()
                )).toList()
        ));
    }

    /**
     * GET /api/v1/regulator/recalls - View recall events only
     */
    @GetMapping("/recalls")
    public ResponseEntity<?> getRecallEvents() {
        List<RecallEvent> recalls = recallEventRepository.findByStatusOrderByInitiatedAtDesc(
                RecallEvent.RecallStatus.ACTIVE);
        log.info("Regulator accessed recall events: {} records", recalls.size());
        return ResponseEntity.ok(Map.of(
                "totalRecalls", recalls.size(),
                "recalls", recalls.stream().map(r -> Map.of(
                        "id", r.getId(),
                        "batchNumber", r.getBatch().getBatchNumber(),
                        "medicineName", r.getBatch().getMedicineName(),
                        "recallType", r.getRecallType().name(),
                        "reason", r.getReason(),
                        "initiatedAt", r.getInitiatedAt().toString(),
                        "status", r.getStatus().name()
                )).toList()
        ));
    }

    /**
     * POST /api/v1/regulator/kill-switch - Emergency recall for a batch
     */
    @PostMapping("/kill-switch")
    public ResponseEntity<?> emergencyKillSwitch(@RequestBody Map<String, String> request,
                                                   Authentication authentication) {
        String serialNumber = request.get("serialNumber");
        String reason = request.getOrDefault("reason", "Emergency recall by regulator");

        log.warn("EMERGENCY KILL-SWITCH activated by {} for: {}", authentication.getName(), serialNumber);

        try {
            int blockedCount = hierarchyKillSwitchService.killParentAndChildren(serialNumber, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "blockedUnits", blockedCount,
                    "serialNumber", serialNumber,
                    "reason", reason,
                    "activatedBy", authentication.getName()
            ));
        } catch (Exception e) {
            log.error("Kill-switch failed for: {}", serialNumber, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/regulator/flagged-batches - View flagged batches only
     */
    @GetMapping("/flagged-batches")
    public ResponseEntity<?> getFlaggedBatches() {
        List<Batch> recalled = batchRepository.findByStatus(Batch.BatchStatus.RECALLED);
        List<Batch> autoRecalled = batchRepository.findByStatus(Batch.BatchStatus.RECALLED_AUTO);
        List<Batch> quarantined = batchRepository.findByStatus(Batch.BatchStatus.QUARANTINE);

        // Privacy: only expose safety-relevant fields, NOT production volumes or business metrics
        return ResponseEntity.ok(Map.of(
                "recalled", recalled.stream().map(b -> Map.of(
                        "batchNumber", b.getBatchNumber(),
                        "medicineName", b.getMedicineName(),
                        "status", b.getStatus().name(),
                        "expiryDate", b.getExpiryDate().toString()
                )).toList(),
                "autoRecalled", autoRecalled.stream().map(b -> Map.of(
                        "batchNumber", b.getBatchNumber(),
                        "medicineName", b.getMedicineName(),
                        "status", b.getStatus().name()
                )).toList(),
                "quarantined", quarantined.stream().map(b -> Map.of(
                        "batchNumber", b.getBatchNumber(),
                        "medicineName", b.getMedicineName(),
                        "status", b.getStatus().name()
                )).toList()
        ));
    }

    /**
     * GET /api/v1/regulator/audit-logs - View audit trail (no business metrics)
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalLogs", logs.size());
        resp.put("logs", logs.stream().map(l -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("action", l.getAction());
            entry.put("entityType", l.getEntityType() != null ? l.getEntityType() : "");
            entry.put("createdAt", l.getCreatedAt().toString());
            entry.put("ipAddress", l.getIpAddress() != null ? l.getIpAddress() : "");
            return entry;
        }).toList());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/v1/regulator/batch-location/{batchNumber}
     * Shows where every unit of a batch currently is in the supply chain.
     * Groups by distributor/retailer with full shop identity.
     */
    @GetMapping("/batch-location/{batchNumber}")
    public ResponseEntity<?> getBatchLocation(@PathVariable String batchNumber) {
        var batchOpt = batchRepository.findByBatchNumber(batchNumber);
        if (batchOpt.isEmpty()) return ResponseEntity.notFound().build();

        var batch = batchOpt.get();
        List<UnitItem> units = unitItemRepository.findByBatchId(batch.getId());

        // Build per-unit location list
        List<Map<String, Object>> unitLocations = units.stream().map(unit -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("serialNumber", unit.getSerialNumber());
            info.put("unitType", unit.getUnitType() != null ? unit.getUnitType().name() : "TABLET");
            info.put("status", unit.getStatus().name());
            info.put("isActive", unit.getIsActive());

            if (unit.getCurrentOwner() != null) {
                User owner = unit.getCurrentOwner();
                info.put("currentOwnerRole", owner.getRole().name());
                info.put("currentOwnerEmail", owner.getEmail());
                info.put("currentOwnerName", owner.getFullName() != null ? owner.getFullName() : "");
                info.put("shopName", owner.getShopName() != null ? owner.getShopName() : "");
                info.put("shopAddress", owner.getShopAddress() != null ? owner.getShopAddress() : "");
                info.put("licenseNumber", owner.getLicenseNumber() != null ? owner.getLicenseNumber() : "");
                info.put("phoneNumber", owner.getPhoneNumber() != null ? owner.getPhoneNumber() : "");
                info.put("gstNumber", owner.getGstNumber() != null ? owner.getGstNumber() : "");
                info.put("cityState", owner.getCityState() != null ? owner.getCityState() : "");
            } else {
                info.put("currentOwnerRole", "MANUFACTURER");
                info.put("currentOwnerEmail", batch.getManufacturer() != null ? batch.getManufacturer().getEmail() : "unknown");
                info.put("currentOwnerName", batch.getManufacturer() != null ? batch.getManufacturer().getFullName() : "");
                info.put("shopName", "");
                info.put("shopAddress", "");
                info.put("licenseNumber", "");
                info.put("phoneNumber", "");
            }

            // Last scan location
            List<ScanLog> scans = scanLogRepository.findByUnitIdOrderByScannedAtDesc(unit.getId());
            if (!scans.isEmpty()) {
                ScanLog last = scans.get(0);
                info.put("lastScannedAt", last.getScannedAt().toString());
                info.put("lastScanLocation", last.getLocationLat() != null
                    ? last.getLocationLat() + "," + last.getLocationLng() : "unknown");
                info.put("lastScanResult", last.getScanResult().name());
            } else {
                info.put("lastScannedAt", null);
                info.put("lastScanLocation", "not scanned yet");
                info.put("lastScanResult", "NONE");
            }
            return info;
        }).toList();

        // Summary: count per role
        Map<String, Long> summary = units.stream()
            .filter(u -> u.getCurrentOwner() != null)
            .collect(Collectors.groupingBy(u -> u.getCurrentOwner().getRole().name(), Collectors.counting()));
        long withManufacturer = units.stream().filter(u -> u.getCurrentOwner() == null).count();
        if (withManufacturer > 0) summary.put("MANUFACTURER", withManufacturer);

        // Distributor breakdown — group units by each distributor with their shop identity
        Map<String, Map<String, Object>> distributorBreakdown = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Object>> retailerBreakdown = new java.util.LinkedHashMap<>();

        for (UnitItem unit : units) {
            User owner = unit.getCurrentOwner();
            if (owner == null) continue;
            String key = owner.getEmail();

            if (owner.getRole() == User.Role.DISTRIBUTOR) {
                distributorBreakdown.computeIfAbsent(key, k -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("email", owner.getEmail());
                    d.put("name", owner.getFullName() != null ? owner.getFullName() : "");
                    d.put("shopName", owner.getShopName() != null ? owner.getShopName() : "N/A");
                    d.put("shopAddress", owner.getShopAddress() != null ? owner.getShopAddress() : "N/A");
                    d.put("licenseNumber", owner.getLicenseNumber() != null ? owner.getLicenseNumber() : "N/A");
                    d.put("phoneNumber", owner.getPhoneNumber() != null ? owner.getPhoneNumber() : "N/A");
                    d.put("gstNumber", owner.getGstNumber() != null ? owner.getGstNumber() : "N/A");
                    d.put("cityState", owner.getCityState() != null ? owner.getCityState() : "N/A");
                    d.put("unitCount", 0L);
                    return d;
                });
                distributorBreakdown.get(key).put("unitCount",
                    (Long) distributorBreakdown.get(key).get("unitCount") + 1);
            } else if (owner.getRole() == User.Role.RETAILER || owner.getRole() == User.Role.PHARMACIST) {
                retailerBreakdown.computeIfAbsent(key, k -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("email", owner.getEmail());
                    r.put("name", owner.getFullName() != null ? owner.getFullName() : "");
                    r.put("role", owner.getRole().name());
                    r.put("shopName", owner.getShopName() != null ? owner.getShopName() : "N/A");
                    r.put("shopAddress", owner.getShopAddress() != null ? owner.getShopAddress() : "N/A");
                    r.put("licenseNumber", owner.getLicenseNumber() != null ? owner.getLicenseNumber() : "N/A");
                    r.put("phoneNumber", owner.getPhoneNumber() != null ? owner.getPhoneNumber() : "N/A");
                    r.put("cityState", owner.getCityState() != null ? owner.getCityState() : "N/A");
                    r.put("unitCount", 0L);
                    return r;
                });
                retailerBreakdown.get(key).put("unitCount",
                    (Long) retailerBreakdown.get(key).get("unitCount") + 1);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNumber", batch.getBatchNumber());
        result.put("medicineName", batch.getMedicineName());
        result.put("batchStatus", batch.getStatus().name());
        result.put("expiryDate", batch.getExpiryDate().toString());
        result.put("totalUnits", units.size());
        result.put("summary", summary);
        result.put("distributors", new java.util.ArrayList<>(distributorBreakdown.values()));
        result.put("retailers", new java.util.ArrayList<>(retailerBreakdown.values()));
        result.put("units", unitLocations);
        return ResponseEntity.ok(result);
    }
}
