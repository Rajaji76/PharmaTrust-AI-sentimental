package pharmatrust.manufacturing_system.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI Sentinel Service for fraud detection and anomaly analysis
 * Implements geographic anomaly detection, automatic kill-switch, and device fingerprinting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISentinelService {

    private final ScanLogRepository scanLogRepository;
    private final UnitItemRepository unitItemRepository;
    private final AlertRepository alertRepository;
    private final RecallEventRepository recallEventRepository;
    private final BlockchainService blockchainService;

    @Value("${ai.max-travel-speed-kmh:200}")
    private double maxTravelSpeedKmh;

    @Value("${ai.simultaneous-scan-threshold-minutes:5}")
    private long simultaneousScanThresholdMinutes;

    @Value("${ai.auto-killswitch-enabled:true}")
    private boolean autoKillSwitchEnabled;

    /**
     * Analyze scan pattern and detect anomalies
     * Returns anomaly score between 0.0 (normal) and 1.0 (critical fraud)
     */
    @Transactional
    public float analyzeScanPattern(UUID unitId, ScanLog currentScan) {
        log.info("Analyzing scan pattern for unit: {}", unitId);

        // Get recent scans for this unit
        List<ScanLog> recentScans = scanLogRepository
                .findTop5ByUnitIdOrderByScannedAtDesc(unitId);

        if (recentScans.isEmpty()) {
            // First scan - no anomaly
            currentScan.setAnomalyScore(0.0f);
            currentScan.setAutoFlagged(false);
            return 0.0f;
        }

        float anomalyScore = 0.0f;

        // Check for geographic anomalies
        boolean geographicAnomaly = detectGeographicAnomaly(unitId, currentScan, recentScans);
        if (geographicAnomaly) {
            anomalyScore = 1.0f; // Critical - impossible travel
        }

        // Check for device fingerprint changes
        float deviceScore = analyzeDeviceChanges(currentScan, recentScans);
        anomalyScore = Math.max(anomalyScore, deviceScore);

        // Store anomaly score
        currentScan.setAnomalyScore(anomalyScore);

        // Auto-flag if score is high
        if (anomalyScore >= 0.7f) {
            currentScan.setAutoFlagged(true);
            log.warn("Scan auto-flagged with anomaly score: {}", anomalyScore);
        }

        // Trigger kill-switch for critical anomalies
        if (anomalyScore >= 1.0f && autoKillSwitchEnabled) {
            autoTriggerKillSwitch(unitId, "Critical anomaly detected: impossible travel or simultaneous scans");
        }

        return anomalyScore;
    }

    /**
     * Detect geographic anomalies (impossible travel, simultaneous scans)
     * Returns true if critical anomaly detected
     */
    public boolean detectGeographicAnomaly(UUID unitId, ScanLog currentScan, List<ScanLog> recentScans) {
        if (recentScans.isEmpty()) {
            return false;
        }

        ScanLog previousScan = recentScans.get(0);

        // Skip if either scan doesn't have location data
        if (currentScan.getLocationLat() == null || currentScan.getLocationLng() == null ||
            previousScan.getLocationLat() == null || previousScan.getLocationLng() == null) {
            return false;
        }

        // Calculate distance between scans using Haversine formula
        double distance = calculateDistance(
                previousScan.getLocationLat(), previousScan.getLocationLng(),
                currentScan.getLocationLat(), currentScan.getLocationLng()
        );

        // Calculate time difference
        long timeDiffMinutes = Duration.between(
                previousScan.getScannedAt(),
                currentScan.getScannedAt()
        ).toMinutes();

        // Prevent division by zero
        if (timeDiffMinutes == 0) {
            timeDiffMinutes = 1;
        }

        // Calculate speed in km/h
        double actualSpeed = (distance / timeDiffMinutes) * 60;

        log.debug("Geographic analysis - Distance: {} km, Time: {} min, Speed: {} km/h",
                distance, timeDiffMinutes, actualSpeed);

        // Check 1: Impossible travel (speed > max allowed)
        boolean impossibleTravel = actualSpeed > maxTravelSpeedKmh;

        // Check 2: Simultaneous scans (< threshold minutes apart, > 50 km distance)
        boolean simultaneousScans = timeDiffMinutes < simultaneousScanThresholdMinutes && distance > 50;

        if (impossibleTravel) {
            log.warn("IMPOSSIBLE TRAVEL DETECTED - Unit: {}, Speed: {} km/h, Distance: {} km, Time: {} min",
                    unitId, actualSpeed, distance, timeDiffMinutes);

            generateAlert(
                    AlertType.GEOGRAPHIC_FRAUD,
                    String.format("Impossible travel detected: %.2f km in %d minutes (%.2f km/h)",
                            distance, timeDiffMinutes, actualSpeed),
                    unitId
            );
            return true;
        }

        if (simultaneousScans) {
            log.warn("SIMULTANEOUS SCANS DETECTED - Unit: {}, Distance: {} km, Time: {} min",
                    unitId, distance, timeDiffMinutes);

            generateAlert(
                    AlertType.GEOGRAPHIC_FRAUD,
                    String.format("Simultaneous scans detected: %.2f km apart in %d minutes",
                            distance, timeDiffMinutes),
                    unitId
            );
            return true;
        }
        return false;
    }

    /**
     * Calculate distance between two geographic points using Haversine formula
     * Returns distance in kilometers
     */
    private double calculateDistance(String lat1Str, String lng1Str, String lat2Str, String lng2Str) {
        try {
            double lat1 = Double.parseDouble(lat1Str);
            double lng1 = Double.parseDouble(lng1Str);
            double lat2 = Double.parseDouble(lat2Str);
            double lng2 = Double.parseDouble(lng2Str);

            final int EARTH_RADIUS_KM = 6371;

            double dLat = Math.toRadians(lat2 - lat1);
            double dLng = Math.toRadians(lng2 - lng1);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLng / 2) * Math.sin(dLng / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            return EARTH_RADIUS_KM * c;
        } catch (NumberFormatException e) {
            log.error("Invalid coordinate format", e);
            return 0.0;
        }
    }

    /**
     * Analyze device fingerprint changes across scans
     * Returns anomaly score based on device changes
     */
    private float analyzeDeviceChanges(ScanLog currentScan, List<ScanLog> recentScans) {
        if (recentScans.isEmpty()) {
            return 0.0f;
        }

        int deviceChanges = 0;
        int ipChanges = 0;

        for (ScanLog previousScan : recentScans) {
            // Check device fingerprint change
            if (currentScan.getDeviceFingerprint() != null &&
                previousScan.getDeviceFingerprint() != null &&
                !currentScan.getDeviceFingerprint().equals(previousScan.getDeviceFingerprint())) {
                deviceChanges++;
            }

            // Check IP address change
            if (currentScan.getIpAddress() != null &&
                previousScan.getIpAddress() != null &&
                !currentScan.getIpAddress().equals(previousScan.getIpAddress())) {
                ipChanges++;
            }
        }

        // Multiple device changes are suspicious
        if (deviceChanges >= 3) {
            log.warn("Multiple device changes detected: {} changes in recent scans", deviceChanges);
            return 0.7f; // High suspicion
        }

        if (deviceChanges >= 2 && ipChanges >= 2) {
            log.warn("Multiple device and IP changes detected");
            return 0.6f; // Medium-high suspicion
        }

        if (deviceChanges >= 1 || ipChanges >= 1) {
            return 0.3f; // Low suspicion
        }

        return 0.0f;
    }

    /**
     * Generate unique device fingerprint from HTTP request
     * Combines User-Agent, screen resolution, timezone, language
     */
    public String generateDeviceFingerprint(HttpServletRequest request) {
        StringBuilder fingerprint = new StringBuilder();

        // User-Agent
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            fingerprint.append(userAgent);
        }

        // Accept-Language
        String language = request.getHeader("Accept-Language");
        if (language != null) {
            fingerprint.append("|").append(language);
        }

        // Accept-Encoding
        String encoding = request.getHeader("Accept-Encoding");
        if (encoding != null) {
            fingerprint.append("|").append(encoding);
        }

        // Custom headers for screen resolution, timezone (if provided by client)
        String screenResolution = request.getHeader("X-Screen-Resolution");
        if (screenResolution != null) {
            fingerprint.append("|").append(screenResolution);
        }

        String timezone = request.getHeader("X-Timezone");
        if (timezone != null) {
            fingerprint.append("|").append(timezone);
        }

        // Generate hash of the fingerprint
        String fingerprintStr = fingerprint.toString();
        return String.valueOf(fingerprintStr.hashCode());
    }

    /**
     * Automatically trigger kill-switch for a unit
     * Sets is_active = false and status = RECALLED_AUTO
     */
    @Transactional
    public void autoTriggerKillSwitch(UUID unitId, String reason) {
        log.warn("AUTO KILL-SWITCH TRIGGERED - Unit: {}, Reason: {}", unitId, reason);

        UnitItem unit = unitItemRepository.findById(unitId)
                .orElseThrow(() -> new RuntimeException("Unit not found: " + unitId));

        // Deactivate unit
        unit.setIsActive(false);
        unit.setStatus(UnitItem.UnitStatus.RECALLED_AUTO);
        unitItemRepository.save(unit);

        // Create recall event
        RecallEvent recallEvent = new RecallEvent();
        recallEvent.setBatch(unit.getBatch());
        recallEvent.setInitiatedBy(unit.getBatch().getManufacturer());
        recallEvent.setRecallType(RecallEvent.RecallType.AUTO);
        recallEvent.setReason(reason);
        recallEvent.setStatus(RecallEvent.RecallStatus.ACTIVE);
        recallEventRepository.save(recallEvent);

        // Emit recall event on blockchain asynchronously (FR-020)
        final String batchNumber = unit.getBatch().getBatchNumber();
        final String recallReason = reason;
        CompletableFuture.runAsync(() -> {
            try {
                String txHash = blockchainService.emitRecallEvent(
                        batchNumber, "AI_SENTINEL", recallReason, true);
                log.info("Auto-recall event emitted on blockchain — batch: {}, txHash: {}", batchNumber, txHash);
            } catch (Exception ex) {
                log.error("Failed to emit auto-recall event on blockchain for batch: {}", batchNumber, ex);
            }
        });

        // Generate critical alert
        generateAlert(
                AlertType.AUTO_RECALL,
                String.format("Unit automatically recalled: %s", reason),
                unitId
        );

        log.info("Unit {} automatically recalled and deactivated", unitId);
    }

    /**
     * Generate alert for fraud detection or anomalies
     */
    public void generateAlert(AlertType type, String message, UUID entityId) {
        Alert alert = new Alert();
        alert.setAlertType(type == AlertType.GEOGRAPHIC_FRAUD || type == AlertType.AUTO_RECALL
                ? Alert.AlertType.GEOGRAPHIC_FRAUD : Alert.AlertType.SUSPICIOUS_SCAN);
        alert.setMessage(message);
        alert.setEntityId(entityId);
        alert.setSeverity(type == AlertType.GEOGRAPHIC_FRAUD || type == AlertType.AUTO_RECALL
                ? Alert.Severity.CRITICAL : Alert.Severity.HIGH);
        alert.setAcknowledged(false);

        alertRepository.save(alert);
        log.info("Alert generated - Type: {}, Message: {}", type, message);
    }

    /**
     * Calculate overall anomaly score for a scan
     * Combines multiple factors into single score
     */
    public float calculateAnomalyScore(UUID unitId, ScanLog currentScan) {
        return analyzeScanPattern(unitId, currentScan);
    }

    /**
     * Alert types for fraud detection
     */
    public enum AlertType {
        GEOGRAPHIC_FRAUD,
        SUSPICIOUS_SCAN,
        AUTO_RECALL,
        DEVICE_ANOMALY,
        SCAN_LIMIT_EXCEEDED
    }
}
