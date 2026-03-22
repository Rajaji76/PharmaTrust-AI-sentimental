package pharmatrust.manufacturing_system.dto;

import pharmatrust.manufacturing_system.entity.Batch;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class BatchResponse {
    
    private UUID id;
    private String batchNumber;
    private String medicineName;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String manufacturerName;
    private String status;
    private Integer totalUnits;
    private String merkleRoot;
    private String blockchainTxId;
    private String blockchainTokenId;
    private Double aiVerificationScore;
    private String digitalSignature;
    private List<UnitItemResponse> units;
    private String labReportHash;
    private String labReportS3Key;
    private String manufacturerCompany;
    
    // Constructors
    public BatchResponse() {}
    
    public static BatchResponse fromEntity(Batch batch) {
        BatchResponse response = new BatchResponse();
        response.setId(batch.getId());
        response.setBatchNumber(batch.getBatchNumber());
        response.setMedicineName(batch.getMedicineName());
        response.setManufacturingDate(batch.getManufacturingDate());
        response.setExpiryDate(batch.getExpiryDate());
        if (batch.getManufacturer() != null) {
            response.setManufacturerName(batch.getManufacturer().getFullName());
            response.setManufacturerCompany(batch.getManufacturer().getShopName());
        }
        response.setStatus(batch.getStatus().name());
        response.setTotalUnits(batch.getTotalUnits());
        response.setMerkleRoot(batch.getMerkleRoot());
        response.setBlockchainTxId(batch.getBlockchainTxId());
        response.setDigitalSignature(batch.getDigitalSignature());
        response.setLabReportHash(batch.getLabReportHash());
        response.setLabReportS3Key(batch.getLabReportS3Key());
        return response;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalUnits() {
        return totalUnits;
    }

    public void setTotalUnits(Integer totalUnits) {
        this.totalUnits = totalUnits;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public String getBlockchainTxId() {
        return blockchainTxId;
    }

    public void setBlockchainTxId(String blockchainTxId) {
        this.blockchainTxId = blockchainTxId;
    }

    public String getBlockchainTokenId() {
        return blockchainTokenId;
    }

    public void setBlockchainTokenId(String blockchainTokenId) {
        this.blockchainTokenId = blockchainTokenId;
    }

    public Double getAiVerificationScore() {
        return aiVerificationScore;
    }

    public void setAiVerificationScore(Double aiVerificationScore) {
        this.aiVerificationScore = aiVerificationScore;
    }

    public String getDigitalSignature() {
        return digitalSignature;
    }

    public void setDigitalSignature(String digitalSignature) {
        this.digitalSignature = digitalSignature;
    }

    public List<UnitItemResponse> getUnits() {
        return units;
    }

    public void setUnits(List<UnitItemResponse> units) {
        this.units = units;
    }

    public String getLabReportHash() { return labReportHash; }
    public void setLabReportHash(String labReportHash) { this.labReportHash = labReportHash; }

    public String getLabReportS3Key() { return labReportS3Key; }
    public void setLabReportS3Key(String labReportS3Key) { this.labReportS3Key = labReportS3Key; }

    public String getManufacturerCompany() { return manufacturerCompany; }
    public void setManufacturerCompany(String manufacturerCompany) { this.manufacturerCompany = manufacturerCompany; }
}
