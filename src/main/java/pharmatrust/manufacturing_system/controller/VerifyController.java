package pharmatrust.manufacturing_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.model.UnitItem;
import pharmatrust.manufacturing_system.model.Batch;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;
import pharmatrust.manufacturing_system.repository.BatchRepository;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/verify")
public class VerifyController {

    @Autowired private UnitItemRepository unitRepo;
    @Autowired private BatchRepository batchRepo;

    // ... बाकी कोड ऊपर का सेम रहेगा ...

@GetMapping("/check")
public ResponseEntity<?> verifyProduct(@RequestParam String sn) {
    return unitRepo.findBySerialNumber(sn)
            .map(unit -> {
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("serialNumber", unit.getSerialNumber());
                response.put("status", "GENUINE");
                response.put("scanCount", unit.getScanCount());
                response.put("batchId", unit.getBatchId());
                
                // .ok() के अंदर (Object) कास्ट करना ज़रूरी है ताकि एरर न आये
                return ResponseEntity.ok((Object) response); 
            })
            .orElse(ResponseEntity.status(404).body((Object) "Product not found or invalid serial number"));
}

}
