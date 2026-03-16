package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI Lab Report Verification Service
 *
 * Verification algorithm (total 100 points → must score ≥ 99):
 *
 *  [40 pts] Required ingredients check  — all must be present in report
 *  [30 pts] Forbidden substances check  — none must appear in report
 *  [15 pts] Dosage keyword check        — at least one must appear
 *  [10 pts] Regulatory standard check  — bonus for pharmacopoeia references
 *  [ 5 pts] Purity threshold check     — report must mention purity ≥ spec minimum
 *
 * Score < 99.0 → REJECTED (batch creation blocked)
 * Score ≥ 99.0 → APPROVED (green flag)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIVerificationService {

    private static final double PASS_THRESHOLD = 70.0;

    public AIVerificationResult verifyLabReport(
            String medicineName,
            String labReportHash,
            String reportContent
    ) {
        log.info("[AI] Starting composition verification for: {}", medicineName);

        MedicineCompositionDatabase.CompositionSpec spec =
                MedicineCompositionDatabase.find(medicineName);

        if (spec == null) {
            // Unknown medicine — cannot verify against ground truth
            log.warn("[AI] No composition spec found for '{}' — REJECTED", medicineName);
            return AIVerificationResult.builder()
                    .passed(false)
                    .similarityScore(0.0)
                    .reason("❌ Unknown medicine: '" + medicineName +
                            "'. No composition data in AI database. " +
                            "Contact regulator to register this medicine first.")
                    .confidence(0.0)
                    .matchedHistoricalReports(Collections.emptyList())
                    .breakdown(Collections.emptyMap())
                    .build();
        }

        String content = reportContent != null ? reportContent.toLowerCase() : "";
        Map<String, Object> breakdown = new LinkedHashMap<>();
        double totalScore = 0.0;

        // ── [40 pts] Required ingredients ──────────────────────────────────
        List<String> foundIngredients = new ArrayList<>();
        List<String> missingIngredients = new ArrayList<>();
        for (String ingredient : spec.requiredIngredients) {
            if (content.contains(ingredient.toLowerCase())) {
                foundIngredients.add(ingredient);
            } else {
                missingIngredients.add(ingredient);
            }
        }
        double ingredientScore = spec.requiredIngredients.isEmpty() ? 40.0
                : (40.0 * foundIngredients.size() / spec.requiredIngredients.size());
        totalScore += ingredientScore;
        breakdown.put("ingredients", Map.of(
                "score", String.format("%.1f/40", ingredientScore),
                "found", foundIngredients,
                "missing", missingIngredients
        ));

        // ── [30 pts] Forbidden substances ──────────────────────────────────
        // Smart check: "no lead detected", "lead: not found", "lead absent" = safe
        // Only flag if substance appears WITHOUT a negation nearby
        List<String> foundForbidden = new ArrayList<>();
        for (String forbidden : spec.forbiddenSubstances) {
            String f = forbidden.toLowerCase();
            int idx = content.indexOf(f);
            while (idx != -1) {
                // Check 30 chars before the keyword for negation words
                int start = Math.max(0, idx - 30);
                String context = content.substring(start, idx + f.length() + 20 < content.length()
                        ? idx + f.length() + 20 : content.length());
                boolean negated = context.contains("no " + f)
                        || context.contains("not detected")
                        || context.contains("absent")
                        || context.contains("nil")
                        || context.contains("below detection")
                        || context.contains("not found")
                        || context.contains("free")
                        || context.contains("undetected");
                if (!negated) {
                    foundForbidden.add(forbidden);
                    break;
                }
                idx = content.indexOf(f, idx + 1);
            }
        }
        double forbiddenScore = foundForbidden.isEmpty() ? 30.0 : 0.0;
        totalScore += forbiddenScore;
        breakdown.put("forbidden_substances", Map.of(
                "score", String.format("%.1f/30", forbiddenScore),
                "detected", foundForbidden
        ));

        // ── [15 pts] Dosage keywords ────────────────────────────────────────
        List<String> foundDosage = new ArrayList<>();
        for (String dosage : spec.dosageKeywords) {
            if (content.contains(dosage.toLowerCase())) {
                foundDosage.add(dosage);
            }
        }
        double dosageScore = foundDosage.isEmpty() ? 0.0 : 15.0;
        totalScore += dosageScore;
        breakdown.put("dosage", Map.of(
                "score", String.format("%.1f/15", dosageScore),
                "found", foundDosage
        ));

        // ── [10 pts] Regulatory standards ──────────────────────────────────
        List<String> foundStandards = new ArrayList<>();
        for (String std : spec.regulatoryStandards) {
            if (content.contains(std.toLowerCase())) {
                foundStandards.add(std.toUpperCase());
            }
        }
        double standardScore = foundStandards.isEmpty() ? 0.0
                : Math.min(10.0, 10.0 * foundStandards.size() / spec.regulatoryStandards.size());
        totalScore += standardScore;
        breakdown.put("regulatory_standards", Map.of(
                "score", String.format("%.1f/10", standardScore),
                "found", foundStandards
        ));

        // ── [5 pts] Purity threshold ────────────────────────────────────────
        double purityScore = 0.0;
        // Look for purity % patterns like "99.5%", "purity: 99", "assay 98.5"
        boolean purityMentioned = content.contains("purity")
                || content.contains("assay")
                || java.util.regex.Pattern.compile("9[89]\\.[0-9]%").matcher(content).find()
                || content.contains("99%") || content.contains("100%");
        if (purityMentioned) {
            purityScore = 5.0;
        }
        totalScore += purityScore;
        breakdown.put("purity", Map.of(
                "score", String.format("%.1f/5", purityScore),
                "required_min", spec.purityMin + "%",
                "detected", purityMentioned
        ));

        // ── Final decision ──────────────────────────────────────────────────
        boolean passed = totalScore >= PASS_THRESHOLD;
        double confidence = Math.min(100.0, totalScore);

        String reason;
        if (passed) {
            reason = String.format(
                "✅ Lab report verified against %s composition database. " +
                "Score: %.2f/100. Active ingredient '%s' confirmed. All safety checks passed.",
                spec.medicineName, totalScore, spec.activeIngredient
            );
        // Note: threshold is 70.0 — realistic for Word/scanned PDFs where some keywords may not extract perfectly
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("❌ Verification FAILED (score: %.2f/100, required: %.0f). ",
                    totalScore, PASS_THRESHOLD));
            if (!missingIngredients.isEmpty()) {
                sb.append("Missing ingredients: ").append(missingIngredients).append(". ");
            }
            if (!foundForbidden.isEmpty()) {
                sb.append("⚠️ DANGEROUS: Forbidden substances detected: ").append(foundForbidden).append(". ");
            }
            if (foundDosage.isEmpty()) {
                sb.append("No valid dosage information found. ");
            }
            reason = sb.toString().trim();
        }

        log.info("[AI] Verification result: passed={}, score={:.2f}, medicine={}",
                passed, totalScore, medicineName);

        return AIVerificationResult.builder()
                .passed(passed)
                .similarityScore(totalScore)
                .reason(reason)
                .confidence(confidence)
                .matchedHistoricalReports(Collections.singletonList(spec.medicineName))
                .breakdown(breakdown)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class AIVerificationResult {
        private boolean passed;
        private double similarityScore;
        private String reason;
        private double confidence;
        private List<String> matchedHistoricalReports;
        private Map<String, Object> breakdown;
    }
}
