package pharmatrust.manufacturing_system.controller;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.model.UnitItem; // <--- यह ज़रूरी था
import pharmatrust.manufacturing_system.service.ManufacturerService;
import java.util.List; // <--- यह ज़रूरी था
import org.springframework.http.ResponseEntity;


@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/manufacturer")
public class ManufacturerController {

    @Autowired
    private ManufacturerService manufacturerService;
    @Autowired
private UnitItemRepository unitItemRepository;

    @PostMapping("/create-batch")
public String createBatch(
        @RequestParam String name,
        @RequestParam String batchNo,
        @RequestParam int qty,
        @RequestParam double api,    // नया: API लेवल के लिए
        @RequestParam double purity) { // नया: शुद्धता के लिए
    
    // अब यह सर्विस को 5 चीजें भेजेगा
    return manufacturerService.createBatch(name, batchNo, qty, api, purity);
}

        @GetMapping("/view-batch/{batchNo}")
public List<UnitItem> viewBatch(@PathVariable String batchNo) {
    // यह उस बैच की सारी 1000 बोतलें और उनके Box IDs निकाल कर देगा
    return manufacturerService.getUnitsByBatchNumber(batchNo);
}
// ManufacturerController.java में यहाँ जोड़ें:
@GetMapping("/view-box/{boxId}")
public ResponseEntity<List<UnitItem>> viewBoxUnits(@PathVariable String boxId) {
    // Repository से उस बॉक्स की सारी बोतलें निकालें
    List<UnitItem> units = unitItemRepository.findByParentBoxId(boxId);
    return ResponseEntity.ok(units);
}

    @GetMapping("/verify/{serialNumber}")
public ResponseEntity<?> verifyProduct(@PathVariable String serialNumber) {
    try {
        // Service से डेटा मंगवाएं
        UnitItem item = manufacturerService.verifyProduct(serialNumber);
        return ResponseEntity.ok(item);
    } catch (Exception e) {
        // अगर सीरियल नंबर गलत है
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid QR Code");
    }
}

}




