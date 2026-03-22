package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.service.HierarchyKillSwitchService;
import pharmatrust.manufacturing_system.service.TOTPService;

import java.util.Map;

/**
 * Advanced Security Features Controller
 * 
 * Endpoints for:
 * 1. Parent-Child Hierarchy Kill Switch
 * 2. TOTP Offline Verification
 * 3. Private Blockchain Commitments
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Slf4j
public class AdvancedSecurityController {

    private final HierarchyKillSwitchService hierarchyKillSwitchService;
    private final TOTPService totpService;

    /**
     * Kill parent unit and all children recursively
     * 
     * Use Case: Regulator scans stolen carton and blocks all 10,000 units inside
     * 
     * POST /api/v1/security/kill-hierarchy
     * Body: { "parentSerialNumber": "CARTON-001", "reason": "STOLEN" }
     */
    @PostMapping("/kill-hierarchy")
    @PreAuthorize("hasAuthority('REGULATOR')")
    public ResponseEntity<?> killHierarchy(@RequestBody KillHierarchyRequest request) {
        log.info("Kill hierarchy requested for parent: {} - Reason: {}", 
                request.getParentSerialNumber(), request.getReason());
        
        try {
            int blockedCount = hierarchyKillSwitchService.killParentAndChildren(
                    request.getParentSerialNumber(),
                    request.getReason()
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully blocked " + blockedCount + " units",
                    "blockedCount", blockedCount,
                    "parentSerialNumber", request.getParentSerialNumber()
            ));
            
        } catch (Exception e) {
            log.error("Failed to kill hierarchy", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get hierarchy statistics for a parent unit
     * 
     * GET /api/v1/security/hierarchy-stats/{serialNumber}
     */
    @GetMapping("/hierarchy-stats/{serialNumber}")
    @PreAuthorize("hasAnyAuthority('REGULATOR', 'MANUFACTURER')")
    public ResponseEntity<?> getHierarchyStats(@PathVariable String serialNumber) {
        try {
            HierarchyKillSwitchService.HierarchyStats stats = 
                    hierarchyKillSwitchService.getHierarchyStats(serialNumber);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to get hierarchy stats", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Check if unit is part of blocked hierarchy
     * 
     * GET /api/v1/security/check-blocked/{serialNumber}
     */
    @GetMapping("/check-blocked/{serialNumber}")
    public ResponseEntity<?> checkIfBlocked(@PathVariable String serialNumber) {
        try {
            boolean isBlocked = hierarchyKillSwitchService.isPartOfBlockedHierarchy(serialNumber);
            
            return ResponseEntity.ok(Map.of(
                    "serialNumber", serialNumber,
                    "isBlocked", isBlocked,
                    "message", isBlocked ? "Unit is part of blocked hierarchy" : "Unit is active"
            ));
            
        } catch (Exception e) {
            log.error("Failed to check blocked status", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Generate TOTP for offline verification
     * 
     * POST /api/v1/security/generate-totp
     * Body: { "serialNumber": "BATCH-001-00001", "secret": "shared-secret" }
     */
    @PostMapping("/generate-totp")
    @PreAuthorize("hasAnyAuthority('MANUFACTURER', 'DISTRIBUTOR')")
    public ResponseEntity<?> generateTOTP(@RequestBody TOTPRequest request) {
        try {
            String totp = totpService.generateTOTP(request.getSecret(), request.getSerialNumber());
            int validitySeconds = totpService.getRemainingValiditySeconds();
            
            return ResponseEntity.ok(Map.of(
                    "totp", totp,
                    "serialNumber", request.getSerialNumber(),
                    "validitySeconds", validitySeconds,
                    "expiresAt", System.currentTimeMillis() + (validitySeconds * 1000)
            ));
            
        } catch (Exception e) {
            log.error("Failed to generate TOTP", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Verify TOTP for offline verification
     * 
     * POST /api/v1/security/verify-totp
     * Body: { "serialNumber": "BATCH-001-00001", "totp": "12345678", "secret": "shared-secret" }
     */
    @PostMapping("/verify-totp")
    public ResponseEntity<?> verifyTOTP(@RequestBody VerifyTOTPRequest request) {
        try {
            boolean isValid = totpService.verifyTOTP(
                    request.getTotp(),
                    request.getSecret(),
                    request.getSerialNumber()
            );
            
            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "serialNumber", request.getSerialNumber(),
                    "message", isValid ? "TOTP verified successfully" : "Invalid or expired TOTP"
            ));
            
        } catch (Exception e) {
            log.error("Failed to verify TOTP", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== DTOs ====================

    @lombok.Data
    public static class KillHierarchyRequest {
        private String parentSerialNumber;
        private String reason;
    }

    @lombok.Data
    public static class TOTPRequest {
        private String serialNumber;
        private String secret;
    }

    @lombok.Data
    public static class VerifyTOTPRequest {
        private String serialNumber;
        private String totp;
        private String secret;
    }
}
