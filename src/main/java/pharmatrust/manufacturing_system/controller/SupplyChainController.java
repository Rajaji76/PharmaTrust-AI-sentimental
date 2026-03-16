package pharmatrust.manufacturing_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.dto.SupplyChainStatsResponse;
import pharmatrust.manufacturing_system.service.SupplyChainService;

@RestController
@RequestMapping("/api/v1/supply-chain")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
public class SupplyChainController {
    
    @Autowired
    private SupplyChainService supplyChainService;
    
    /**
     * Get supply chain statistics (Regulator only)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('REGULATOR')")
    public ResponseEntity<SupplyChainStatsResponse> getSupplyChainStats() {
        SupplyChainStatsResponse stats = supplyChainService.getSupplyChainStats();
        return ResponseEntity.ok(stats);
    }
}
