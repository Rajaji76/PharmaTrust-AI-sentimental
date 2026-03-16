package pharmatrust.manufacturing_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pharmatrust.manufacturing_system.dto.SupplyChainStatsResponse;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupplyChainService {
    
    @Autowired
    private UnitItemRepository unitItemRepository;
    
    @Autowired
    private ScanLogRepository scanLogRepository;
    
    /**
     * Get supply chain statistics for regulators
     */
    public SupplyChainStatsResponse getSupplyChainStats() {
        SupplyChainStatsResponse stats = new SupplyChainStatsResponse();
        
        // Total units
        Long totalUnits = unitItemRepository.count();
        stats.setTotalUnits(totalUnits);
        
        // Active units
        Long activeUnits = unitItemRepository.countByStatus(UnitItem.UnitStatus.ACTIVE);
        stats.setActiveUnits(activeUnits);
        
        // Recalled units
        Long recalledUnits = unitItemRepository.countByStatus(UnitItem.UnitStatus.RECALLED);
        stats.setRecalledUnits(recalledUnits);
        
        // Expired units
        Long expiredUnits = unitItemRepository.countByStatus(UnitItem.UnitStatus.EXPIRED);
        stats.setExpiredUnits(expiredUnits);
        
        // Units by status
        Map<String, Long> unitsByStatus = new HashMap<>();
        for (UnitItem.UnitStatus status : UnitItem.UnitStatus.values()) {
            Long count = unitItemRepository.countByStatus(status);
            unitsByStatus.put(status.name(), count);
        }
        stats.setUnitsByStatus(unitsByStatus);
        
        // Units by owner type (role)
        Map<String, Long> unitsByOwnerType = new HashMap<>();
        for (User.Role role : User.Role.values()) {
            Long count = unitItemRepository.countByCurrentOwnerRole(role);
            unitsByOwnerType.put(role.name(), count);
        }
        stats.setUnitsByOwnerType(unitsByOwnerType);
        
        // Total scans
        Long totalScans = scanLogRepository.count();
        stats.setTotalScans(totalScans);
        
        // Suspicious scans
        Long suspiciousScans = scanLogRepository.countByScanResult(ScanLog.ScanResult.SUSPICIOUS) +
                              scanLogRepository.countByScanResult(ScanLog.ScanResult.FLAGGED);
        stats.setSuspiciousScans(suspiciousScans);
        
        // Flagged units (scan count >= max limit)
        Long flaggedUnits = unitItemRepository.countFlaggedUnits();
        stats.setFlaggedUnits(flaggedUnits);
        
        return stats;
    }
}
