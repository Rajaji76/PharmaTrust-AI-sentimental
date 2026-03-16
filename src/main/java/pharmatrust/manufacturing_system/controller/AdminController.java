package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.service.ExpiryMonitorService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
@RequiredArgsConstructor
public class AdminController {

    private final ExpiryMonitorService expiryMonitorService;

    /**
     * GET /api/v1/admin/trigger-expiry-check
     * Manually triggers the expiry monitor — useful for testing without waiting for midnight cron.
     */
    @GetMapping("/trigger-expiry-check")
    public ResponseEntity<Map<String, String>> triggerExpiryCheck() {
        expiryMonitorService.runNow();
        return ResponseEntity.ok(Map.of(
            "status", "done",
            "message", "Expiry check completed. Check Regulator → Alerts for results."
        ));
    }
}
