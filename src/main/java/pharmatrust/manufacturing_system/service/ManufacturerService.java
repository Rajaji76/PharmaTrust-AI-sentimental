package pharmatrust.manufacturing_system.service;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.model.Batch;
import pharmatrust.manufacturing_system.model.UnitItem;
import pharmatrust.manufacturing_system.repository.BatchRepository;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;

@Service
public class ManufacturerService {

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UnitItemRepository unitItemRepository;

    // अगर आपने SecurityService नहीं बनाई है, तो इसे कमेंट कर दें या नीचे वाला UUID वाला तरीका अपनाएं
    // @Autowired
    // private SecurityService securityService;

    @Transactional // <--- यहाँ केवल एक ही बार होना चाहिए
    public String createBatch(String name, String batchNo, int qty, double api, double purity) {
        
        // 1. AI Accuracy Logic
        double standardApi = 500.0;
        double standardPurity = 100.0;

        double apiAccuracy = (1 - Math.abs(standardApi - api) / standardApi);
        double purityAccuracy = (purity / standardPurity);
        double finalAccuracy = ((apiAccuracy + purityAccuracy) / 2) * 100;

        // 2. Batch Create करना
        Batch batch = new Batch();
        batch.setMedicineName(name);
        batch.setBatchNumber(batchNo);
        batch.setScanCount(0);
        batch.setAiAccuracyScore(finalAccuracy);
        batch.setCurrentHolder("Manufacturer"); // सप्लाई चेन के लिए जरूरी
        
        if (finalAccuracy >= 99.0) {
            batch.setStatus("APPROVED - INTEGRITY OK");
        } else {
            batch.setStatus("HELD - QUALITY ALERT (" + String.format("%.2f", finalAccuracy) + "%)");
        }
        
        batchRepository.save(batch);

        // 3. Units Loop
        for (int i = 1; i <= qty; i++) {
            int boxNum = ((i - 1) / 100) + 1;
            String boxId = "BOX-" + batchNo + "-" + boxNum;

            UnitItem unit = new UnitItem();
            String serialNo = batchNo + "-" + String.format("%04d", i);
            
            unit.setSerialNumber(serialNo);
            unit.setBatch(batch);
            unit.setParentBoxId(boxId);
            unit.setScanCount(0);
            unit.setMaxScanLimit(5);
            
            // SecurityService की जगह UUID का इस्तेमाल करें ताकि एरर न आए
            unit.setDigitalSignature(UUID.randomUUID().toString());
            
            unitItemRepository.save(unit);
        }
        
        return "AI Analysis: " + String.format("%.2f", finalAccuracy) + "% Accuracy. Units created.";
    }

    public List<UnitItem> getUnitsByBatchNumber(String batchNo) {
        return unitItemRepository.findByBatch_BatchNumber(batchNo);
    }

    public UnitItem verifyProduct(String serialNumber) {
        UnitItem item = unitItemRepository.findById(serialNumber)
                .orElseThrow(() -> new RuntimeException("Product Not Found!"));
        item.setScanCount(item.getScanCount() + 1);
        return unitItemRepository.save(item);
    }

    @Transactional
    public void blockEntireBox(String boxId) {
        List<UnitItem> items = unitItemRepository.findByParentBoxId(boxId);
        for(UnitItem item : items) {
            item.getBatch().setStatus("RECALLED / BLOCKED");
        }
        unitItemRepository.saveAll(items);
    }
}


