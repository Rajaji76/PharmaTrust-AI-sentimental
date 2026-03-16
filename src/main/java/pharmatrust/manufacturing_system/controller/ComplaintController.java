package pharmatrust.manufacturing_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pharmatrust.manufacturing_system.entity.Complaint;
import pharmatrust.manufacturing_system.service.ComplaintService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Slf4j
public class ComplaintController {

    private final ComplaintService complaintService;

    /**
     * Raise a complaint — Distributor or Retailer only
     */
    @PostMapping("/raise")
    @PreAuthorize("hasAnyAuthority('DISTRIBUTOR','RETAILER','PHARMACIST')")
    public ResponseEntity<?> raiseComplaint(@RequestBody Map<String, String> body, Authentication auth) {
        try {
            String serialNumber = body.get("serialNumber");
            String batchNumber = body.getOrDefault("batchNumber", "");
            String medicineName = body.getOrDefault("medicineName", "");
            String issueTypeStr = body.get("issueType");
            String description = body.get("description");

            if (serialNumber == null || serialNumber.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "serialNumber is required"));
            if (description == null || description.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "description is required"));
            if (issueTypeStr == null)
                return ResponseEntity.badRequest().body(Map.of("error", "issueType is required"));

            Complaint.IssueType issueType;
            try {
                issueType = Complaint.IssueType.valueOf(issueTypeStr);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid issueType: " + issueTypeStr));
            }

            Map<String, Object> result = complaintService.raiseComplaint(
                serialNumber, batchNumber, medicineName, issueType, description, auth);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to raise complaint", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get my complaints (Distributor/Retailer)
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('DISTRIBUTOR','RETAILER','PHARMACIST')")
    public ResponseEntity<List<Complaint>> getMyComplaints(Authentication auth) {
        return ResponseEntity.ok(complaintService.getMyComplaints(auth));
    }

    /**
     * Get all complaints (Regulator only)
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('REGULATOR')")
    public ResponseEntity<List<Complaint>> getAllComplaints() {
        return ResponseEntity.ok(complaintService.getAllComplaints());
    }
}
