package pharmatrust.manufacturing_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.model.Batch;
import pharmatrust.manufacturing_system.model.UnitItem;
import pharmatrust.manufacturing_system.repository.BatchRepository;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    @Autowired private BatchRepository batchRepo;
    @Autowired private UnitItemRepository unitRepo;

    @PostMapping("/create")
    public ResponseEntity<?> createBatch(@RequestBody Batch batch, @RequestParam int quantity) {
        // 1. बैच सेव करें
        Batch savedBatch = batchRepo.save(batch);

        // 2. हर डोज़ के लिए यूनिट्स बनाएँ
        for (int i = 0; i < quantity; i++) {
            // Builder की जगह सिंपल तरीके से ऑब्जेक्ट बनायें
            UnitItem unit = new UnitItem();
            
            // यहाँ ध्यान दें: .setBatch() इस्तेमाल करें क्योंकि Model में 'batch' ऑब्जेक्ट है
            unit.setBatch(savedBatch); 
            
            // सीरियल नंबर जेनरेट करें
            String sn = "SN-" + savedBatch.getBatchNumber() + "-" + System.nanoTime() + "-" + i;
            unit.setSerialNumber(sn);
            
            unit.setScanCount(0);
            unit.setMaxScanLimit(5);
            
            // डेटाबेस में सेव करें
            unitRepo.save(unit);
        }

        return ResponseEntity.ok("Batch created with " + quantity + " trackable doses.");
    }
}

