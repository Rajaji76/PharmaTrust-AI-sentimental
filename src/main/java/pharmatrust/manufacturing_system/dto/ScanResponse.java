package pharmatrust.manufacturing_system.dto;

import java.time.LocalDate;

public class ScanResponse {
    
    private Boolean isValid;
    private String result; // VALID, INVALID, SUSPICIOUS, FLAGGED, RECALLED, BLOCKED
    private String message;
    private Integer scanCount;
    private Integer maxScanLimit;
    private Boolean isCounterfeit;
    private Boolean isRecalled;
    private Boolean isBlocked;
    private String recallReason;
    private UnitItemResponse unitDetails;
    private Float anomalyScore;
    
    // Complete Batch Details for Transparency
    private String batchNumber;
    private String medicineName;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String manufacturerName;
    private String batchStatus;
    private String blockchainTxId;
    private String merkleRoot;
    private Double aiVerificationScore;

    // Hierarchy info (for BOX/CARTON scans)
    private String unitType;           // TABLET, STRIP, BOX, CARTON
    private String parentSerialNumber; // parent box serial (if this is a unit inside a box)
    private java.util.List<ChildUnitInfo> childUnits; // child units (if this is a box)
    private Integer childCount;

    public static class ChildUnitInfo {
        private String serialNumber;
        private String unitType;
        private String status;
        private Integer scanCount;

        public ChildUnitInfo() {}
        public ChildUnitInfo(String serialNumber, String unitType, String status, Integer scanCount) {
            this.serialNumber = serialNumber;
            this.unitType = unitType;
            this.status = status;
            this.scanCount = scanCount;
        }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public String getUnitType() { return unitType; }
        public void setUnitType(String unitType) { this.unitType = unitType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getScanCount() { return scanCount; }
        public void setScanCount(Integer scanCount) { this.scanCount = scanCount; }
    }

    // Constructors
    public ScanResponse() {}
    
    public ScanResponse(Boolean isValid, String result, String message) {
        this.isValid = isValid;
        this.result = result;
        this.message = message;
    }
    
    // Getters and Setters
    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public Boolean getIsCounterfeit() {
        return isCounterfeit;
    }

    public void setIsCounterfeit(Boolean isCounterfeit) {
        this.isCounterfeit = isCounterfeit;
    }

    public Boolean getIsRecalled() {
        return isRecalled;
    }

    public void setIsRecalled(Boolean isRecalled) {
        this.isRecalled = isRecalled;
    }

    public Boolean getIsBlocked() {
        return isBlocked;
    }

    public void setIsBlocked(Boolean isBlocked) {
        this.isBlocked = isBlocked;
    }

    public String getRecallReason() {
        return recallReason;
    }

    public void setRecallReason(String recallReason) {
        this.recallReason = recallReason;
    }

    public UnitItemResponse getUnitDetails() {
        return unitDetails;
    }

    public void setUnitDetails(UnitItemResponse unitDetails) {
        this.unitDetails = unitDetails;
    }

    public Float getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(Float anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public LocalDate getManufacturingDate() {
        return manufacturingDate;
    }

    public void setManufacturingDate(LocalDate manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getBatchStatus() {
        return batchStatus;
    }

    public void setBatchStatus(String batchStatus) {
        this.batchStatus = batchStatus;
    }

    public String getBlockchainTxId() {
        return blockchainTxId;
    }

    public void setBlockchainTxId(String blockchainTxId) {
        this.blockchainTxId = blockchainTxId;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public Double getAiVerificationScore() {
        return aiVerificationScore;
    }

    public void setAiVerificationScore(Double aiVerificationScore) {
        this.aiVerificationScore = aiVerificationScore;
    }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public String getParentSerialNumber() { return parentSerialNumber; }
    public void setParentSerialNumber(String parentSerialNumber) { this.parentSerialNumber = parentSerialNumber; }

    public java.util.List<ChildUnitInfo> getChildUnits() { return childUnits; }
    public void setChildUnits(java.util.List<ChildUnitInfo> childUnits) { this.childUnits = childUnits; }

    public Integer getChildCount() { return childCount; }
    public void setChildCount(Integer childCount) { this.childCount = childCount; }
}
