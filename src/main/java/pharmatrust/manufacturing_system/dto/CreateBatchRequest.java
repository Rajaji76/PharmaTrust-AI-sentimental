package pharmatrust.manufacturing_system.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class CreateBatchRequest {
    
    @NotBlank(message = "Medicine name is required")
    private String medicineName;
    
    @NotNull(message = "Manufacturing date is required")
    private LocalDate manufacturingDate;
    
    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;
    
    @NotNull(message = "Total units is required")
    @Min(value = 1, message = "Total units must be at least 1")
    @Max(value = 100000, message = "Total units cannot exceed 100,000")
    private Integer totalUnits;
    
    private String labReportHash;
    
    // Constructors
    public CreateBatchRequest() {}
    
    public CreateBatchRequest(String medicineName, LocalDate manufacturingDate, 
                             LocalDate expiryDate, Integer totalUnits, String labReportHash) {
        this.medicineName = medicineName;
        this.manufacturingDate = manufacturingDate;
        this.expiryDate = expiryDate;
        this.totalUnits = totalUnits;
        this.labReportHash = labReportHash;
    }
    
    // Getters and Setters
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

    public Integer getTotalUnits() {
        return totalUnits;
    }

    public void setTotalUnits(Integer totalUnits) {
        this.totalUnits = totalUnits;
    }

    public String getLabReportHash() {
        return labReportHash;
    }

    public void setLabReportHash(String labReportHash) {
        this.labReportHash = labReportHash;
    }
}
