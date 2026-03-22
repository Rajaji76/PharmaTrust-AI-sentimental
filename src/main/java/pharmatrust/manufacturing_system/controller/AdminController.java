package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.service.ExpiryMonitorService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {

    private final ExpiryMonitorService expiryMonitorService;

    /**
     * GET /api/v1/admin/trigger-expiry-check
     * Manually triggers the expiry monitor — useful for testing without waiting for midnight cron.
     */
    @GetMapping("/trigger-expiry-check")
    @PreAuthorize("hasAnyAuthority('REGULATOR', 'MANUFACTURER')")
    public ResponseEntity<Map<String, String>> triggerExpiryCheck() {
        expiryMonitorService.runNow();
        return ResponseEntity.ok(Map.of(
            "status", "done",
            "message", "Expiry check completed. Check Regulator → Alerts for results."
        ));
    }
}

