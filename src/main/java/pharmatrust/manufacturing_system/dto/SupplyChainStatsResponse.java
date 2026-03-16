package pharmatrust.manufacturing_system.dto;

import java.util.Map;

public class SupplyChainStatsResponse {
    
    private Long totalUnits;
    private Long activeUnits;
    private Long recalledUnits;
    private Long expiredUnits;
    private Map<String, Long> unitsByStatus;
    private Map<String, Long> unitsByOwnerType;
    private Long totalScans;
    private Long suspiciousScans;
    private Long flaggedUnits;
    
    // Constructors
    public SupplyChainStatsResponse() {}
    
    // Getters and Setters
    public Long getTotalUnits() {
        return totalUnits;
    }

    public void setTotalUnits(Long totalUnits) {
        this.totalUnits = totalUnits;
    }

    public Long getActiveUnits() {
        return activeUnits;
    }

    public void setActiveUnits(Long activeUnits) {
        this.activeUnits = activeUnits;
    }

    public Long getRecalledUnits() {
        return recalledUnits;
    }

    public void setRecalledUnits(Long recalledUnits) {
        this.recalledUnits = recalledUnits;
    }

    public Long getExpiredUnits() {
        return expiredUnits;
    }

    public void setExpiredUnits(Long expiredUnits) {
        this.expiredUnits = expiredUnits;
    }

    public Map<String, Long> getUnitsByStatus() {
        return unitsByStatus;
    }

    public void setUnitsByStatus(Map<String, Long> unitsByStatus) {
        this.unitsByStatus = unitsByStatus;
    }

    public Map<String, Long> getUnitsByOwnerType() {
        return unitsByOwnerType;
    }

    public void setUnitsByOwnerType(Map<String, Long> unitsByOwnerType) {
        this.unitsByOwnerType = unitsByOwnerType;
    }

    public Long getTotalScans() {
        return totalScans;
    }

    public void setTotalScans(Long totalScans) {
        this.totalScans = totalScans;
    }

    public Long getSuspiciousScans() {
        return suspiciousScans;
    }

    public void setSuspiciousScans(Long suspiciousScans) {
        this.suspiciousScans = suspiciousScans;
    }

    public Long getFlaggedUnits() {
        return flaggedUnits;
    }

    public void setFlaggedUnits(Long flaggedUnits) {
        this.flaggedUnits = flaggedUnits;
    }
}
