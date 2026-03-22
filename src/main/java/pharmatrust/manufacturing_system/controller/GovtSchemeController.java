package pharmatrust.manufacturing_system.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.UserRepository;
import pharmatrust.manufacturing_system.service.AbhaService;
import pharmatrust.manufacturing_system.service.AbhaService.PatientHealthProfile;
import pharmatrust.manufacturing_system.service.AbhaService.RiskAssessment;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Government Scheme Dispense Controller
 *
 * Handles:
 * 1. Patient ABHA lookup → health profile fetch
 * 2. AI safety check (allergy / contraindication / drug interaction)
 * 3. Dispense record save
 * 4. SMS alert simulation when risk detected
 *
 * Future: Real ABHA API + actual SMS via AWS SNS / MSG91
 */
@RestController
@RequestMapping("/api/v1/govt-scheme")
public class GovtSchemeController {

    private static final Logger log = LoggerFactory.getLogger(GovtSchemeController.class);

    @Autowired
    private AbhaService abhaService;

    @Autowired
    private UserRepository userRepository;

    // In-memory dispense log (replace with DB table in production)
    private final List<Map<String, Object>> dispenseLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * POST /api/v1/govt-scheme/lookup-patient
     * Retailer fetches patient health profile by ABHA ID or phone number.
     */
    @PostMapping("/lookup-patient")
    public ResponseEntity<?> lookupPatient(@RequestBody Map<String, String> body, Authentication auth) {
        String abhaId    = body.getOrDefault("abhaId", "").trim();
        String phone     = body.getOrDefault("phone", "").trim();
        String patientName = body.getOrDefault("patientName", "").trim();

        if (abhaId.isEmpty() && phone.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ABHA ID ya phone number dena zaroori hai"));
        }

        // Use ABHA ID if provided, else generate a lookup key from phone
        String lookupKey = !abhaId.isEmpty() ? abhaId : ("PHONE-" + phone);

        PatientHealthProfile profile = abhaService.fetchHealthProfile(lookupKey);

        // Override name/phone if retailer provided them manually
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("abhaId", lookupKey);
        response.put("patientName", !patientName.isEmpty() ? patientName : profile.patientName);
        response.put("phoneNumber", !phone.isEmpty() ? phone : profile.phoneNumber);
        response.put("age", profile.age);
        response.put("bloodGroup", profile.bloodGroup);
        response.put("knownAllergies", profile.knownAllergies);
        response.put("chronicConditions", profile.chronicConditions);
        response.put("currentMedications", profile.currentMedications);
        response.put("isAbhaVerified", profile.isAbhaVerified);
        response.put("dataSource", profile.dataSource);
        response.put("note", "ABHA_MOCK: Real ABHA API integration pending. Data shown is simulated for demo.");

        log.info("[GovtScheme] Patient lookup by retailer={} for ABHA={}", auth.getName(), lookupKey);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/govt-scheme/check-safety
     * AI safety check before dispensing — returns risk level + message.
     */
    @PostMapping("/check-safety")
    public ResponseEntity<?> checkSafety(@RequestBody Map<String, Object> body, Authentication auth) {
        String abhaId      = (String) body.getOrDefault("abhaId", "");
        String medicineName = (String) body.getOrDefault("medicineName", "");

        if (abhaId.isEmpty() || medicineName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "abhaId aur medicineName required hain"));
        }

        PatientHealthProfile profile = abhaService.fetchHealthProfile(abhaId);
        RiskAssessment risk = abhaService.checkMedicineSafety(profile, medicineName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("riskLevel", risk.level);
        response.put("hasRisk", risk.hasRisk);
        response.put("message", risk.message);
        response.put("patientName", profile.patientName);
        response.put("knownAllergies", profile.knownAllergies);
        response.put("chronicConditions", profile.chronicConditions);
        response.put("currentMedications", profile.currentMedications);

        // Simulate SMS alert if risk detected
        if (risk.hasRisk) {
            String phone = profile.phoneNumber;
            String smsText = buildSmsAlert(profile.patientName, medicineName, risk.message);
            simulateSmsAlert(phone, smsText, auth.getName());
            response.put("smsAlertSent", true);
            response.put("smsAlertPhone", phone != null ? maskPhone(phone) : null);
            response.put("smsPreview", smsText);
        } else {
            response.put("smsAlertSent", false);
        }

        log.info("[GovtScheme] Safety check by retailer={} medicine={} risk={}", auth.getName(), medicineName, risk.level);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/govt-scheme/dispense
     * Save dispense record after retailer confirms.
     */
    @PostMapping("/dispense")
    public ResponseEntity<?> recordDispense(@RequestBody Map<String, Object> body, Authentication auth) {
        String abhaId       = (String) body.getOrDefault("abhaId", "");
        String patientName  = (String) body.getOrDefault("patientName", "");
        String patientPhone = (String) body.getOrDefault("patientPhone", "");
        String medicineName = (String) body.getOrDefault("medicineName", "");
        String batchNumber  = (String) body.getOrDefault("batchNumber", "");
        Object quantityObj  = body.getOrDefault("quantity", 1);
        String govtScheme   = (String) body.getOrDefault("govtScheme", "PMJAY");
        String riskLevel    = (String) body.getOrDefault("riskLevel", "SAFE");
        String notes        = (String) body.getOrDefault("notes", "");

        int quantity = quantityObj instanceof Number ? ((Number) quantityObj).intValue() : 1;

        if (medicineName.isEmpty() || abhaId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "medicineName aur abhaId required hain"));
        }

        // Build dispense record
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", UUID.randomUUID().toString());
        record.put("dispensedAt", LocalDateTime.now().toString());
        record.put("dispensedBy", auth.getName());
        record.put("abhaId", abhaId);
        record.put("patientName", patientName);
        record.put("patientPhone", patientPhone);
        record.put("medicineName", medicineName);
        record.put("batchNumber", batchNumber);
        record.put("quantity", quantity);
        record.put("govtScheme", govtScheme);
        record.put("riskLevel", riskLevel);
        record.put("notes", notes);
        record.put("status", "DISPENSED");

        dispenseLog.add(record);

        log.info("[GovtScheme] Dispense recorded: retailer={} medicine={} qty={} abha={} scheme={}",
            auth.getName(), medicineName, quantity, abhaId, govtScheme);

        // If risk was present but retailer dispensed anyway — log override
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            log.warn("[GovtScheme] ⚠️ RISK OVERRIDE: retailer={} dispensed {} despite {} risk for ABHA={}",
                auth.getName(), medicineName, riskLevel, abhaId);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "dispenseId", record.get("id"),
            "message", "Dispense record saved successfully",
            "dispensedAt", record.get("dispensedAt")
        ));
    }

    /**
     * GET /api/v1/govt-scheme/my-dispense-history
     * Retailer's own dispense history.
     */
    @GetMapping("/my-dispense-history")
    public ResponseEntity<?> getMyDispenseHistory(Authentication auth) {
        String retailerEmail = auth.getName();
        List<Map<String, Object>> myRecords = dispenseLog.stream()
            .filter(r -> retailerEmail.equals(r.get("dispensedBy")))
            .sorted((a, b) -> String.valueOf(b.get("dispensedAt")).compareTo(String.valueOf(a.get("dispensedAt"))))
            .toList();
        return ResponseEntity.ok(myRecords);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildSmsAlert(String patientName, String medicineName, String riskMessage) {
        return String.format(
            "Bharat Sarkar - PharmaTrust Alert: Priya %s, aapko di ja rahi dawai '%s' ke baare mein " +
            "ek important health warning hai: %s " +
            "Kripya apne doctor se salah lein. Helpline: 1800-180-1104",
            patientName != null ? patientName : "Patient",
            medicineName,
            riskMessage
        );
    }

    private void simulateSmsAlert(String phone, String message, String sentBy) {
        // TODO (Future): Replace with real SMS via AWS SNS or MSG91
        // SNS: snsClient.publish(PublishRequest.builder().phoneNumber(phone).message(message).build())
        // MSG91: POST https://api.msg91.com/api/v5/flow/
        log.warn("[SMS-ALERT] [SIMULATED] To={} SentBy={} Message={}",
            phone != null ? maskPhone(phone) : "UNKNOWN", sentBy, message);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "XXXXXX" + phone.substring(phone.length() - 4);
    }
}
