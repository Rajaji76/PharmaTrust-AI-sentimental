package pharmatrust.manufacturing_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.model.Batch;
import pharmatrust.manufacturing_system.repository.BatchRepository;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/regulator")
public class RegulatorController {

    @Autowired
    private BatchRepository batchRepository;

    // डैशबोर्ड के लिए सारे बैच मंगवाना
    @GetMapping("/all-batches")
    public List<Batch> getAllBatches() {
        return batchRepository.findAll();
    }

    // KILL SWITCH: बैच स्टेटस बदलना
    @PostMapping("/recall/{batchNo}")
    public String recallBatch(@PathVariable String batchNo) {
        Batch batch = batchRepository.findByBatchNumber(batchNo);
        if(batch != null) {
            batch.setStatus("RECALLED / BLOCKED BY AUTHORITY");
            batchRepository.save(batch);
            return "SUCCESS: Batch " + batchNo + " has been globally recalled.";
        }
        return "ERROR: Batch not found.";
    }
}
