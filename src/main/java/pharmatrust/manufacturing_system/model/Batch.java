package pharmatrust.manufacturing_system.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.util.Date;

@Entity
@Table(name = "batches")
@Data 
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "medicine_name")
    private String medicineName;

    @Column(name = "batch_number", unique = true) // यूनिक होना ज़रूरी है
    private String batchNumber;

    // AI & Lab Data
    private double apiLevel;
    private double purityLevel;
    private double aiAccuracyScore; 
    private String labReportHash;

    // Supply Chain Tracking
    private String status; // APPROVED, HELD, RECALLED, SOLD (केवल एक बार)
    private String currentHolder; // Manufacturer, Distributor, Retailer
    private String lastUpdatedLocation; 
    
    private int scanCount;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    // प्री-परसिस्ट ताकि डेट अपने आप डल जाए
    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        if (status == null) status = "APPROVED"; // Default status
        if (currentHolder == null) currentHolder = "Manufacturer";
    }
}