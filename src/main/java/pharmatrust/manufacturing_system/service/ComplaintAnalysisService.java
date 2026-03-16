package pharmatrust.manufacturing_system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pharmatrust.manufacturing_system.entity.Alert;
import pharmatrust.manufacturing_system.entity.Complaint;

/**
 * AI-powered complaint analysis service.
 * Analyzes free-text complaint descriptions and determines severity.
 */
@Service
public class ComplaintAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintAnalysisService.class);

    // Keywords mapped to severity levels
    private static final String[] CRITICAL_KEYWORDS = {
        "poison", "toxic", "contaminated", "fake", "counterfeit", "wrong medicine",
        "patient died", "serious reaction", "adulterated", "no seal", "seal broken",
        "tampered", "duplicate", "forged"
    };

    private static final String[] HIGH_KEYWORDS = {
        "damaged", "broken", "leaking", "temperature", "heat", "frozen", "melted",
        "discolored", "smell", "odor", "mold", "fungus", "expired", "expiry",
        "quantity mismatch", "missing units", "wrong batch"
    };

    private static final String[] MEDIUM_KEYWORDS = {
        "dent", "scratch", "label", "printing", "packaging", "wet", "moisture",
        "humidity", "delay", "late delivery", "wrong address"
    };

    public AnalysisResult analyze(String description, Complaint.IssueType issueType) {
        String lower = description.toLowerCase();
        Alert.Severity severity;
        StringBuilder analysis = new StringBuilder();

        // Check issue type first for base severity
        severity = switch (issueType) {
            case WRONG_MEDICINE, SEAL_BROKEN, SUSPICIOUS_APPEARANCE -> Alert.Severity.HIGH;
            case TEMPERATURE_ISSUE, DAMAGED_BOX, EXPIRED_STOCK -> Alert.Severity.MEDIUM;
            default -> Alert.Severity.LOW;
        };

        // Upgrade severity based on keywords in description
        for (String kw : CRITICAL_KEYWORDS) {
            if (lower.contains(kw)) {
                severity = Alert.Severity.CRITICAL;
                analysis.append("🚨 CRITICAL keyword detected: '").append(kw).append("'. ");
                break;
            }
        }
        if (severity != Alert.Severity.CRITICAL) {
            for (String kw : HIGH_KEYWORDS) {
                if (lower.contains(kw)) {
                    if (severity == Alert.Severity.LOW || severity == Alert.Severity.MEDIUM) {
                        severity = Alert.Severity.HIGH;
                    }
                    analysis.append("⚠️ High-risk keyword: '").append(kw).append("'. ");
                    break;
                }
            }
        }
        if (severity == Alert.Severity.LOW) {
            for (String kw : MEDIUM_KEYWORDS) {
                if (lower.contains(kw)) {
                    severity = Alert.Severity.MEDIUM;
                    analysis.append("⚡ Issue keyword: '").append(kw).append("'. ");
                    break;
                }
            }
        }

        // Build analysis summary
        String summary = switch (severity) {
            case CRITICAL -> "🚨 CRITICAL: Immediate regulatory action required. Possible safety threat to patients.";
            case HIGH -> "⚠️ HIGH: Serious quality issue detected. Batch may need quarantine.";
            case MEDIUM -> "⚡ MEDIUM: Quality concern noted. Inspection recommended.";
            case LOW -> "ℹ️ LOW: Minor issue reported. Logged for monitoring.";
        };

        analysis.append(summary);
        log.info("[AI Complaint] issueType={}, severity={}, keywords found in: '{}'",
                issueType, severity, description.substring(0, Math.min(50, description.length())));

        return new AnalysisResult(severity, analysis.toString());
    }

    public record AnalysisResult(Alert.Severity severity, String analysis) {}
}
