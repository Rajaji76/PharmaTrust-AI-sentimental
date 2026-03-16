package pharmatrust.manufacturing_system.dto;

import pharmatrust.manufacturing_system.entity.UnitItem;
import java.util.UUID;

public class UnitItemResponse {
    
    private UUID id;
    private String serialNumber;
    private String batchNumber;
    private String unitType;
    private String status;
    private Integer scanCount;
    private Integer maxScanLimit;
    private String digitalSignature;
    private String qrCodeData;
    private Boolean isActive;
    private String currentOwnerName;
    
    // Constructors
    public UnitItemResponse() {}
    
    public static UnitItemResponse fromEntity(UnitItem unit) {
        UnitItemResponse response = new UnitItemResponse();
        response.setId(unit.getId());
        response.setSerialNumber(unit.getSerialNumber());
        response.setBatchNumber(unit.getBatch() != null ? 
            unit.getBatch().getBatchNumber() : null);
        response.setUnitType(unit.getUnitType() != null ? 
            unit.getUnitType().name() : null);
        response.setStatus(unit.getStatus().name());
        response.setScanCount(unit.getScanCount());
        response.setMaxScanLimit(unit.getMaxScanLimit());
        response.setDigitalSignature(unit.getDigitalSignature());
        response.setIsActive(unit.getIsActive());
        response.setCurrentOwnerName(unit.getCurrentOwner() != null ? 
            unit.getCurrentOwner().getFullName() : null);
        return response;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getScanCount() {
        return scanCount;
    }

    public void setScanCount(Integer scanCount) {
        this.scanCount = scanCount;
    }

    public Integer getMaxScanLimit() {
        return maxScanLimit;
    }

    public void setMaxScanLimit(Integer maxScanLimit) {
        this.maxScanLimit = maxScanLimit;
    }

    public String getDigitalSignature() {
        return digitalSignature;
    }

    public void setDigitalSignature(String digitalSignature) {
        this.digitalSignature = digitalSignature;
    }

    public String getQrCodeData() {
        return qrCodeData;
    }

    public void setQrCodeData(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getCurrentOwnerName() {
        return currentOwnerName;
    }

    public void setCurrentOwnerName(String currentOwnerName) {
        this.currentOwnerName = currentOwnerName;
    }
}
