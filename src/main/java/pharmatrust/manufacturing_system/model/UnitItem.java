package pharmatrust.manufacturing_system.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@Entity
@Table(name = "unit_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitItem {

    @Id
    @Column(name = "serial_number")
    private String serialNumber; 

    private String digitalSignature;
    private int scanCount;
    private int maxScanLimit;

    @Column(name = "parent_box_id")
    private String parentBoxId; 

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Batch batch; // यहाँ हमने बैच ऑब्जेक्ट रखा है

    // --- ये दो मेथड्स आपके एरर फिक्स करेंगे ---
    
    // 1. Batch ID निकालने के लिए (VerifyController के लिए)
    public UUID getBatchId() {
        return (this.batch != null) ? this.batch.getId() : null;
    }

    // 2. Batch Object सेट करने के लिए (BatchController के लिए)
    public void setBatchId(UUID id) {
        if (this.batch == null) {
            this.batch = new Batch();
        }
        this.batch.setId(id);
    }
}
