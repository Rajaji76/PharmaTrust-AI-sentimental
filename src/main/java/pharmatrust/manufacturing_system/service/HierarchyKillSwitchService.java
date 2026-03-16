package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.Alert;
import pharmatrust.manufacturing_system.entity.UnitItem;
import pharmatrust.manufacturing_system.repository.AlertRepository;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hierarchy Kill Switch Service - Recursive Parent-Child Blocking
 * 
 * Problem: If a carton with 10,000 units is stolen, can we block all child units at once?
 * Solution: Parent-Child serialization with recursive SQL queries
 * 
 * Example Hierarchy:
 * - 1 Carton (Parent) → 100 Boxes → 10,000 Strips → 100,000 Tablets
 * - If Carton is killed, all 100,000 tablets are automatically invalidated
 * 
 * Database Design:
 * - unit_items.parent_unit_id (self-referencing foreign key)
 * - Recursive CTE query to find all descendants
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HierarchyKillSwitchService {

    private final UnitItemRepository unitItemRepository;
    private final AlertRepository alertRepository;

    /**
     * Kill parent unit and ALL child units recursively
     * 
     * Use Case: Regulator scans stolen carton QR and blocks entire shipment
     * 
     * @param parentSerialNumber Serial number of parent unit (Carton/Box)
     * @param reason Reason for kill (e.g., "STOLEN", "RECALLED", "COUNTERFEIT")
     * @return Number of units blocked (including parent and all children)
     */
    @Transactional
    public int killParentAndChildren(String parentSerialNumber, String reason) {
        log.info("Initiating kill switch for parent: {} - Reason: {}", parentSerialNumber, reason);
        
        // Find parent unit
        UnitItem parentUnit = unitItemRepository.findBySerialNumber(parentSerialNumber)
                .orElseThrow(() -> new RuntimeException("Parent unit not found: " + parentSerialNumber));
        
        // Get all child units recursively
        List<UnitItem> allUnits = new ArrayList<>();
        allUnits.add(parentUnit);
        collectAllChildren(parentUnit, allUnits);
        
        log.info("Found {} total units (1 parent + {} children) to block", 
                allUnits.size(), allUnits.size() - 1);
        
        // Block all units
        int blockedCount = 0;
        for (UnitItem unit : allUnits) {
            if (unit.getStatus() != UnitItem.UnitStatus.RECALLED_AUTO) {
                unit.setStatus(UnitItem.UnitStatus.RECALLED_AUTO);
                unit.setIsActive(false);
                unitItemRepository.save(unit);
                blockedCount++;
                
                // Create alert for each blocked unit
                createBlockAlert(unit, reason, parentSerialNumber);
            }
        }
        
        log.info("Successfully blocked {} units under parent: {}", blockedCount, parentSerialNumber);
        return blockedCount;
    }

    /**
     * Recursively collect all child units (DFS traversal)
     * 
     * @param parent Parent unit
     * @param collector List to collect all descendants
     */
    private void collectAllChildren(UnitItem parent, List<UnitItem> collector) {
        List<UnitItem> children = unitItemRepository.findByParentUnit(parent);
        
        for (UnitItem child : children) {
            collector.add(child);
            // Recursive call for grandchildren
            collectAllChildren(child, collector);
        }
    }

    /**
     * Get count of all child units under parent (without loading entities)
     * Efficient for large hierarchies
     * 
     * @param parentId Parent unit ID
     * @return Total count of descendants
     */
    public long countAllChildren(UUID parentId) {
        return unitItemRepository.countByParentUnitId(parentId);
    }

    /**
     * Check if unit has any children
     * Used to prevent accidental deletion of parent units
     */
    public boolean hasChildren(UUID unitId) {
        return unitItemRepository.existsByParentUnitId(unitId);
    }

    /**
     * Get hierarchy depth for a unit
     * Example: Tablet=0, Strip=1, Box=2, Carton=3
     * 
     * @param serialNumber Unit serial number
     * @return Depth level (0 = leaf node)
     */
    public int getHierarchyDepth(String serialNumber) {
        UnitItem unit = unitItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Unit not found: " + serialNumber));
        
        int depth = 0;
        UnitItem current = unit;
        
        while (current.getParentUnit() != null) {
            depth++;
            current = current.getParentUnit();
        }
        
        return depth;
    }

    /**
     * Get root parent (top-level carton) for any unit
     * 
     * @param serialNumber Unit serial number
     * @return Root parent unit
     */
    public UnitItem getRootParent(String serialNumber) {
        UnitItem unit = unitItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Unit not found: " + serialNumber));
        
        UnitItem current = unit;
        while (current.getParentUnit() != null) {
            current = current.getParentUnit();
        }
        
        return current;
    }

    /**
     * Verify if unit is part of a blocked hierarchy
     * Used during scan to check if parent was killed
     * 
     * @param serialNumber Unit serial number
     * @return true if unit or any ancestor is blocked
     */
    public boolean isPartOfBlockedHierarchy(String serialNumber) {
        UnitItem unit = unitItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Unit not found: " + serialNumber));
        
        // Check if unit itself is blocked
        if (unit.getStatus() == UnitItem.UnitStatus.RECALLED_AUTO || !unit.getIsActive()) {
            return true;
        }
        
        // Check all ancestors
        UnitItem current = unit.getParentUnit();
        while (current != null) {
            if (current.getStatus() == UnitItem.UnitStatus.RECALLED_AUTO || !current.getIsActive()) {
                log.warn("Unit {} is part of blocked hierarchy. Parent {} is blocked.", 
                        serialNumber, current.getSerialNumber());
                return true;
            }
            current = current.getParentUnit();
        }
        
        return false;
    }

    /**
     * Create alert for blocked unit
     */
    private void createBlockAlert(UnitItem unit, String reason, String parentSerialNumber) {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.TAMPERING_DETECTED)
                .severity(Alert.Severity.CRITICAL)
                .message(String.format("Unit %s blocked via hierarchy kill switch. Parent: %s. Reason: %s",
                        unit.getSerialNumber(), parentSerialNumber, reason))
                .entityId(unit.getId())
                .entityType("UNIT_ITEM")
                .acknowledged(false)
                .createdAt(Instant.now())
                .build();
        
        alertRepository.save(alert);
    }

    /**
     * Get hierarchy statistics for a parent unit
     * Useful for regulator dashboard
     * 
     * @param parentSerialNumber Parent unit serial number
     * @return Statistics object with counts by unit type
     */
    public HierarchyStats getHierarchyStats(String parentSerialNumber) {
        UnitItem parentUnit = unitItemRepository.findBySerialNumber(parentSerialNumber)
                .orElseThrow(() -> new RuntimeException("Parent unit not found: " + parentSerialNumber));
        
        List<UnitItem> allUnits = new ArrayList<>();
        allUnits.add(parentUnit);
        collectAllChildren(parentUnit, allUnits);
        
        HierarchyStats stats = new HierarchyStats();
        stats.totalUnits = allUnits.size();
        stats.activeUnits = (int) allUnits.stream().filter(u -> u.getIsActive()).count();
        stats.blockedUnits = (int) allUnits.stream()
                .filter(u -> u.getStatus() == UnitItem.UnitStatus.RECALLED_AUTO).count();
        
        // Count by unit type
        for (UnitItem unit : allUnits) {
            if (unit.getUnitType() != null) {
                stats.countByType.merge(unit.getUnitType().name(), 1, Integer::sum);
            }
        }
        
        return stats;
    }

    /**
     * Hierarchy statistics DTO
     */
    public static class HierarchyStats {
        public int totalUnits;
        public int activeUnits;
        public int blockedUnits;
        public java.util.Map<String, Integer> countByType = new java.util.HashMap<>();
    }
}
