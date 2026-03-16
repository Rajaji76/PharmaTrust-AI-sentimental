package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.Alert;
import pharmatrust.manufacturing_system.entity.Complaint;
import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.AlertRepository;
import pharmatrust.manufacturing_system.repository.ComplaintRepository;
import pharmatrust.manufacturing_system.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final AlertRepository alertRepository;
    private final ComplaintAnalysisService analysisService;

    @Transactional
    public Map<String, Object> raiseComplaint(
            String serialNumber,
            String batchNumber,
            String medicineName,
            Complaint.IssueType issueType,
            String description,
            Authentication authentication
    ) {
        User reporter = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // AI analysis
        ComplaintAnalysisService.AnalysisResult result = analysisService.analyze(description, issueType);

        // Build alert message with reporter identity
        String alertMsg = String.format(
            "[%s COMPLAINT] %s (%s, %s, License: %s) reported %s on serial %s (batch: %s). " +
            "Description: %s. AI Analysis: %s",
            result.severity(),
            reporter.getShopName() != null ? reporter.getShopName() : reporter.getFullName(),
            reporter.getRole(),
            reporter.getCityState() != null ? reporter.getCityState() : "N/A",
            reporter.getLicenseNumber() != null ? reporter.getLicenseNumber() : "N/A",
            issueType.name().replace("_", " "),
            serialNumber,
            batchNumber != null ? batchNumber : "N/A",
            description,
            result.analysis()
        );

        // Raise alert to regulator
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.TAMPERING_DETECTED)
                .severity(result.severity())
                .message(alertMsg)
                .entityType("COMPLAINT")
                .build();
        alert = alertRepository.save(alert);

        // Save complaint
        Complaint complaint = Complaint.builder()
                .reporter(reporter)
                .serialNumber(serialNumber)
                .batchNumber(batchNumber)
                .medicineName(medicineName)
                .issueType(issueType)
                .description(description)
                .aiSeverity(result.severity())
                .aiAnalysis(result.analysis())
                .alertId(alert.getId())
                .status(result.severity() == Alert.Severity.CRITICAL || result.severity() == Alert.Severity.HIGH
                        ? Complaint.Status.ESCALATED : Complaint.Status.OPEN)
                .build();
        complaint = complaintRepository.save(complaint);

        log.info("[Complaint] Raised by {} — severity={}, alertId={}", reporter.getEmail(), result.severity(), alert.getId());

        return Map.of(
            "complaintId", complaint.getId().toString(),
            "alertId", alert.getId().toString(),
            "severity", result.severity().name(),
            "analysis", result.analysis(),
            "status", complaint.getStatus().name(),
            "message", "Complaint submitted. Regulator has been alerted."
        );
    }

    public List<Complaint> getMyComplaints(Authentication authentication) {
        User reporter = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return complaintRepository.findByReporterOrderByCreatedAtDesc(reporter);
    }

    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }
}
