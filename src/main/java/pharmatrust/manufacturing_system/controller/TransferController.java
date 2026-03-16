package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ownership Transfer endpoints
 * Requirements: FR-016, FR-017
 */
@RestController
@RequestMapping("/api/v1/transfer")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final UnitItemRepository unitItemRepository;
    private final UserRepository userRepository;
    private final OwnershipLogRepository ownershipLogRepository;

    /**
     * POST /api/v1/transfer/initiate - Initiate unit transfer
     */
    @PostMapping("/initiate")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER','DISTRIBUTOR','RETAILER','PHARMACIST')")
    public ResponseEntity<?> initiateTransfer(@RequestBody Map<String, String> request,
                                               Authentication authentication) {
        String serialNumber = request.get("serialNumber");
        String toUserEmail = request.get("toUserEmail");
        String transferTypeStr = request.get("transferType");
        String location = request.getOrDefault("location", "");

        UnitItem unit = unitItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Unit not found: " + serialNumber));

        User fromUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        User toUser = userRepository.findByEmail(toUserEmail)
                .orElseThrow(() -> new RuntimeException("Recipient not found: " + toUserEmail));

        if (!unit.getIsActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unit is not active"));
        }

        OwnershipLog.TransferType transferType;
        try {
            transferType = OwnershipLog.TransferType.valueOf(transferTypeStr);
        } catch (Exception e) {
            transferType = OwnershipLog.TransferType.MANUFACTURE_TO_DISTRIBUTOR;
        }

        // Create ownership log
        OwnershipLog ownershipLog = OwnershipLog.builder()
                .unit(unit)
                .fromUser(fromUser)
                .toUser(toUser)
                .transferType(transferType)
                .location(location)
                .notes("Transfer initiated by " + fromUser.getEmail())
                .build();
        ownershipLogRepository.save(ownershipLog);

        // Update unit owner
        unit.setCurrentOwner(toUser);
        unit.setStatus(UnitItem.UnitStatus.TRANSFERRED);
        unitItemRepository.save(unit);

        log.info("Transfer completed: {} -> {} for unit {}", fromUser.getEmail(), toUserEmail, serialNumber);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "serialNumber", serialNumber,
                "from", fromUser.getEmail(),
                "to", toUserEmail,
                "transferType", transferType.name(),
                "transferId", ownershipLog.getId()
        ));
    }

    /**
     * GET /api/v1/transfer/history/{serial} - Get ownership timeline
     */
    @GetMapping("/history/{serial}")
    public ResponseEntity<?> getOwnershipHistory(@PathVariable String serial) {
        UnitItem unit = unitItemRepository.findBySerialNumber(serial).orElse(null);
        if (unit == null) return ResponseEntity.notFound().build();

        List<OwnershipLog> logs = ownershipLogRepository.getSupplyChainTimeline(unit.getId());
        List<Map<String, Object>> timeline = logs.stream().map(ol -> Map.<String, Object>of(
                "transferredAt", ol.getTransferredAt().toString(),
                "from", ol.getFromUser() != null ? ol.getFromUser().getEmail() : "manufacturer",
                "to", ol.getToUser().getEmail(),
                "transferType", ol.getTransferType().name(),
                "location", ol.getLocation() != null ? ol.getLocation() : ""
        )).toList();

        return ResponseEntity.ok(Map.of(
                "serialNumber", serial,
                "currentOwner", unit.getCurrentOwner() != null ? unit.getCurrentOwner().getEmail() : "unknown",
                "timeline", timeline
        ));
    }

    /**
     * GET /api/v1/transfer/pending - Get pending transfers for current user
     */
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPendingTransfers(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<OwnershipLog> pending = ownershipLogRepository.findPendingTransfersForUser(user.getId());
        return ResponseEntity.ok(Map.of("pendingTransfers", pending.size(), "transfers", pending));
    }

    /**
     * POST /api/v1/transfer/receive - Distributor/Retailer scans a box QR to confirm receipt.
     * Sets currentOwner to the logged-in user and logs the ownership transfer.
     * Body: { "serialNumber": "BOX-...", "notes": "optional" }
     */
    @PostMapping("/receive")
    @PreAuthorize("hasAnyAuthority('DISTRIBUTOR','RETAILER','PHARMACIST')")
    public ResponseEntity<?> receiveStock(@RequestBody Map<String, String> request,
                                          Authentication authentication) {
        String serialNumber = request.get("serialNumber");
        String notes = request.getOrDefault("notes", "");

        if (serialNumber == null || serialNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "serialNumber is required"));
        }

        UnitItem unit = unitItemRepository.findBySerialNumber(serialNumber).orElse(null);
        if (unit == null) return ResponseEntity.badRequest().body(Map.of("error", "Unit not found: " + serialNumber));
        if (!unit.getIsActive()) return ResponseEntity.badRequest().body(Map.of("error", "Unit is blocked/recalled"));

        User receiver = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        User previousOwner = unit.getCurrentOwner();

        // Determine transfer type based on roles
        OwnershipLog.TransferType transferType;
        if (receiver.getRole() == User.Role.DISTRIBUTOR) {
            transferType = OwnershipLog.TransferType.MANUFACTURE_TO_DISTRIBUTOR;
        } else {
            transferType = OwnershipLog.TransferType.DISTRIBUTOR_TO_PHARMACY;
        }

        // Log the ownership transfer
        OwnershipLog log = OwnershipLog.builder()
                .unit(unit)
                .fromUser(previousOwner)
                .toUser(receiver)
                .transferType(transferType)
                .location(receiver.getShopAddress() != null ? receiver.getShopAddress() : "")
                .notes("Received by " + receiver.getEmail()
                    + (receiver.getShopName() != null ? " (" + receiver.getShopName() + ")" : "")
                    + (notes.isBlank() ? "" : " — " + notes))
                .build();
        ownershipLogRepository.save(log);

        // Update unit owner
        unit.setCurrentOwner(receiver);
        unit.setStatus(UnitItem.UnitStatus.TRANSFERRED);
        unitItemRepository.save(unit);

        // Also receive all child units (if this is a BOX/CARTON)
        List<UnitItem> children = unitItemRepository.findByParentUnitId(unit.getId());
        for (UnitItem child : children) {
            OwnershipLog childLog = OwnershipLog.builder()
                    .unit(child)
                    .fromUser(previousOwner)
                    .toUser(receiver)
                    .transferType(transferType)
                    .location(receiver.getShopAddress() != null ? receiver.getShopAddress() : "")
                    .notes("Auto-received with parent " + serialNumber)
                    .build();
            ownershipLogRepository.save(childLog);
            child.setCurrentOwner(receiver);
            child.setStatus(UnitItem.UnitStatus.TRANSFERRED);
            unitItemRepository.save(child);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("serialNumber", serialNumber);
        resp.put("unitType", unit.getUnitType() != null ? unit.getUnitType().name() : "UNIT");
        resp.put("receivedBy", receiver.getEmail());
        resp.put("shopName", receiver.getShopName() != null ? receiver.getShopName() : "");
        resp.put("childUnitsReceived", children.size());
        resp.put("totalUnitsReceived", 1 + children.size());
        resp.put("batchNumber", unit.getBatch().getBatchNumber());
        resp.put("medicineName", unit.getBatch().getMedicineName());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/v1/transfer/my-history - Get all ownership logs where current user was sender or receiver
     */
    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyHistory(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<OwnershipLog> sent = ownershipLogRepository.findByFromUserIdOrderByTransferredAtDesc(user.getId());
        List<OwnershipLog> received = ownershipLogRepository.findByToUserIdOrderByTransferredAtDesc(user.getId());

        // Merge and deduplicate by id
        java.util.Map<UUID, OwnershipLog> merged = new java.util.LinkedHashMap<>();
        received.forEach(l -> merged.put(l.getId(), l));
        sent.forEach(l -> merged.put(l.getId(), l));

        List<Map<String, Object>> result = merged.values().stream()
                .sorted((a, b) -> b.getTransferredAt().compareTo(a.getTransferredAt()))
                .map(ol -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", ol.getId().toString());
                    m.put("serialNumber", ol.getUnit().getSerialNumber());
                    m.put("medicineName", ol.getUnit().getBatch().getMedicineName());
                    m.put("batchNumber", ol.getUnit().getBatch().getBatchNumber());
                    m.put("from", ol.getFromUser() != null ? ol.getFromUser().getEmail() : "manufacturer");
                    m.put("to", ol.getToUser() != null ? ol.getToUser().getEmail() : "");
                    m.put("transferType", ol.getTransferType().name());
                    m.put("status", "COMPLETED");
                    m.put("createdAt", ol.getTransferredAt().toString());
                    m.put("notes", ol.getNotes() != null ? ol.getNotes() : "");
                    return m;
                }).toList();

        return ResponseEntity.ok(result);
    }


    @GetMapping("/my-stock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyStock(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UnitItem> myUnits = unitItemRepository.findByCurrentOwnerId(user.getId());

        // Group by batch
        Map<String, Map<String, Object>> byBatch = new java.util.LinkedHashMap<>();
        for (UnitItem unit : myUnits) {
            String bn = unit.getBatch().getBatchNumber();
            byBatch.computeIfAbsent(bn, k -> {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("batchNumber", bn);
                b.put("medicineName", unit.getBatch().getMedicineName());
                b.put("expiryDate", unit.getBatch().getExpiryDate().toString());
                b.put("batchStatus", unit.getBatch().getStatus().name());
                b.put("unitCount", 0L);
                b.put("boxCount", 0L);
                return b;
            });
            byBatch.get(bn).put("unitCount", (Long) byBatch.get(bn).get("unitCount") + 1);
            if (unit.getUnitType() == UnitItem.UnitType.BOX || unit.getUnitType() == UnitItem.UnitType.CARTON) {
                byBatch.get(bn).put("boxCount", (Long) byBatch.get(bn).get("boxCount") + 1);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ownerEmail", user.getEmail());
        resp.put("shopName", user.getShopName() != null ? user.getShopName() : "");
        resp.put("totalUnits", myUnits.size());
        resp.put("stockByBatch", new java.util.ArrayList<>(byBatch.values()));
        return ResponseEntity.ok(resp);
    }
}
