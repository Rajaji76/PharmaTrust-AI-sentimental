package pharmatrust.manufacturing_system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * ABHA (Ayushman Bharat Health Account) Integration Service
 *
 * CURRENT STATE: Mock/simulated health data for demo purposes.
 *
 * FUTURE INTEGRATION:
 *   - NHA ABHA API: https://abha.abdm.gov.in/
 *   - Endpoint: POST /v1/registration/aadhaar/verifyOtp
 *   - Health Records: FHIR R4 standard via ABDM HIU (Health Information User)
 *   - Consent flow: Patient gives consent → HIU fetches records from HIP
 *   - Replace fetchHealthProfile() with real API call once ABDM sandbox credentials available
 */
@Service
public class AbhaService {

    private static final Logger log = LoggerFactory.getLogger(AbhaService.class);

    public static class PatientHealthProfile {
        public final String abhaId;
        public final String patientName;
        public final String phoneNumber;
        public final int age;
        public final String bloodGroup;
        public final List<String> knownAllergies;       // e.g. ["Penicillin", "Aspirin"]
        public final List<String> chronicConditions;    // e.g. ["Diabetes Type 2", "Hypertension"]
        public final List<String> currentMedications;   // e.g. ["Metformin 500mg", "Amlodipine 5mg"]
        public final List<String> contraindicatedDrugs; // drugs that must NOT be given
        public final boolean isAbhaVerified;
        public final String dataSource; // "ABHA_LIVE" | "ABHA_MOCK" | "NOT_FOUND"

        public PatientHealthProfile(String abhaId, String patientName, String phoneNumber,
                                    int age, String bloodGroup,
                                    List<String> knownAllergies, List<String> chronicConditions,
                                    List<String> currentMedications, List<String> contraindicatedDrugs,
                                    boolean isAbhaVerified, String dataSource) {
            this.abhaId = abhaId;
            this.patientName = patientName;
            this.phoneNumber = phoneNumber;
            this.age = age;
            this.bloodGroup = bloodGroup;
            this.knownAllergies = knownAllergies;
            this.chronicConditions = chronicConditions;
            this.currentMedications = currentMedications;
            this.contraindicatedDrugs = contraindicatedDrugs;
            this.isAbhaVerified = isAbhaVerified;
            this.dataSource = dataSource;
        }
    }

    /**
     * Fetch patient health profile by ABHA ID.
     *
     * TODO (Future): Replace mock data with real ABDM API call:
     *   POST https://healthidsbx.abdm.gov.in/api/v1/search/existsByHealthId
     *   Then fetch FHIR records via HIU consent flow.
     */
    public PatientHealthProfile fetchHealthProfile(String abhaId) {
        log.info("[ABHA] Fetching health profile for ABHA ID: {} (MOCK MODE)", abhaId);

        // Simulate different patient profiles based on ABHA ID suffix for demo
        String suffix = abhaId.replaceAll("[^0-9]", "");
        int seed = suffix.isEmpty() ? 0 : Integer.parseInt(suffix.substring(Math.max(0, suffix.length() - 2))) % 5;

        return switch (seed) {
            case 0 -> new PatientHealthProfile(
                abhaId, "Ramesh Kumar", "9876543210", 45, "B+",
                Arrays.asList("Penicillin", "Sulfonamides"),
                Arrays.asList("Hypertension", "Type 2 Diabetes"),
                Arrays.asList("Metformin 500mg", "Amlodipine 5mg", "Atorvastatin 10mg"),
                Arrays.asList("Penicillin", "Amoxicillin", "Ampicillin"),
                true, "ABHA_MOCK"
            );
            case 1 -> new PatientHealthProfile(
                abhaId, "Sunita Devi", "9123456780", 62, "O+",
                Arrays.asList("Aspirin", "NSAIDs"),
                Arrays.asList("Chronic Kidney Disease", "Anemia"),
                Arrays.asList("Erythropoietin", "Iron Sucrose"),
                Arrays.asList("Aspirin", "Ibuprofen", "Naproxen", "Diclofenac"),
                true, "ABHA_MOCK"
            );
            case 2 -> new PatientHealthProfile(
                abhaId, "Arjun Singh", "9988776655", 28, "A+",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true, "ABHA_MOCK"
            );
            case 3 -> new PatientHealthProfile(
                abhaId, "Priya Sharma", "9765432100", 35, "AB-",
                Arrays.asList("Codeine", "Tramadol"),
                Arrays.asList("Asthma", "Anxiety Disorder"),
                Arrays.asList("Salbutamol Inhaler", "Montelukast 10mg"),
                Arrays.asList("Codeine", "Tramadol", "Morphine", "Beta-blockers"),
                true, "ABHA_MOCK"
            );
            default -> new PatientHealthProfile(
                abhaId, "Patient", null, 0, "Unknown",
                List.of(), List.of(), List.of(), List.of(),
                false, "ABHA_MOCK"
            );
        };
    }

    /**
     * Check if a medicine is safe for a patient given their health profile.
     * Returns a risk assessment.
     */
    public RiskAssessment checkMedicineSafety(PatientHealthProfile profile, String medicineName) {
        String medLower = medicineName.toLowerCase();
        StringBuilder riskReason = new StringBuilder();
        boolean hasRisk = false;
        String riskLevel = "SAFE";

        // Check known allergies
        for (String allergy : profile.knownAllergies) {
            if (medLower.contains(allergy.toLowerCase()) || allergy.toLowerCase().contains(medLower)) {
                riskReason.append("⚠️ ALLERGY ALERT: Patient is allergic to ").append(allergy).append(". ");
                hasRisk = true;
                riskLevel = "CRITICAL";
            }
        }

        // Check contraindicated drugs
        for (String contra : profile.contraindicatedDrugs) {
            if (medLower.contains(contra.toLowerCase()) || contra.toLowerCase().contains(medLower)) {
                riskReason.append("🚫 CONTRAINDICATION: ").append(contra)
                    .append(" is contraindicated for this patient's condition. ");
                hasRisk = true;
                riskLevel = "CRITICAL";
            }
        }

        // Check drug interactions with current medications
        List<String> interactions = checkDrugInteractions(medicineName, profile.currentMedications);
        if (!interactions.isEmpty()) {
            riskReason.append("💊 DRUG INTERACTION: May interact with ")
                .append(String.join(", ", interactions)).append(". ");
            hasRisk = true;
            if (!"CRITICAL".equals(riskLevel)) riskLevel = "HIGH";
        }

        // Condition-specific warnings
        String conditionWarning = checkConditionWarnings(medicineName, profile.chronicConditions);
        if (conditionWarning != null) {
            riskReason.append(conditionWarning);
            hasRisk = true;
            if ("SAFE".equals(riskLevel)) riskLevel = "MEDIUM";
        }

        if (!hasRisk) {
            return new RiskAssessment("SAFE", "No known risks identified for this patient.", false);
        }

        return new RiskAssessment(riskLevel, riskReason.toString().trim(), true);
    }

    private List<String> checkDrugInteractions(String medicine, List<String> currentMeds) {
        String medLower = medicine.toLowerCase();
        // Basic interaction rules
        Map<String, List<String>> interactionMap = Map.of(
            "warfarin",    Arrays.asList("aspirin", "ibuprofen", "paracetamol"),
            "metformin",   Arrays.asList("alcohol", "contrast dye"),
            "amlodipine",  Arrays.asList("simvastatin", "cyclosporine"),
            "atorvastatin", Arrays.asList("clarithromycin", "erythromycin")
        );
        for (Map.Entry<String, List<String>> entry : interactionMap.entrySet()) {
            if (medLower.contains(entry.getKey())) {
                return currentMeds.stream()
                    .filter(m -> entry.getValue().stream().anyMatch(i -> m.toLowerCase().contains(i)))
                    .toList();
            }
        }
        return List.of();
    }

    private String checkConditionWarnings(String medicine, List<String> conditions) {
        String medLower = medicine.toLowerCase();
        for (String condition : conditions) {
            String condLower = condition.toLowerCase();
            if (condLower.contains("kidney") && (medLower.contains("nsaid") || medLower.contains("ibuprofen"))) {
                return "⚠️ CAUTION: NSAIDs should be avoided in kidney disease patients. ";
            }
            if (condLower.contains("asthma") && medLower.contains("aspirin")) {
                return "⚠️ CAUTION: Aspirin can trigger asthma attacks. ";
            }
            if (condLower.contains("diabetes") && medLower.contains("steroid")) {
                return "⚠️ CAUTION: Steroids can raise blood sugar in diabetic patients. ";
            }
        }
        return null;
    }

    public static class RiskAssessment {
        public final String level;   // SAFE | MEDIUM | HIGH | CRITICAL
        public final String message;
        public final boolean hasRisk;

        public RiskAssessment(String level, String message, boolean hasRisk) {
            this.level = level;
            this.message = message;
            this.hasRisk = hasRisk;
        }
    }
}
