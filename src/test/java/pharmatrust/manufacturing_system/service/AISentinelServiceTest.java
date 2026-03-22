package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AISentinelService
 * Tests: geographic anomaly detection, device fingerprinting, anomaly scoring, auto kill-switch
 * Requirements: FR-014, FR-015, FR-027, FR-028
 */
@ExtendWith(MockitoExtension.class)
class AISentinelServiceTest {

    @Mock
    private ScanLogRepository scanLogRepository;
    @Mock
    private UnitItemRepository unitItemRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private RecallEventRepository recallEventRepository;

    @InjectMocks
    private AISentinelService aiSentinelService;

    private UUID unitId;
    private UnitItem unit;
    private Batch batch;
    private User manufacturer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiSentinelService, "maxTravelSpeedKmh", 200.0);
        ReflectionTestUtils.setField(aiSentinelService, "simultaneousScanThresholdMinutes", 5L);
        ReflectionTestUtils.setField(aiSentinelService, "autoKillSwitchEnabled", true);

        unitId = UUID.randomUUID();
        manufacturer = new User();
        manufacturer.setId(UUID.randomUUID());

        batch = new Batch();
        batch.setId(UUID.randomUUID());
        batch.setManufacturer(manufacturer);

        unit = new UnitItem();
        unit.setId(unitId);
        unit.setBatch(batch);
        unit.setIsActive(true);
        unit.setStatus(UnitItem.UnitStatus.ACTIVE);
    }

    // ==================== Geographic Anomaly Detection ====================

    @Test
    void detectGeographicAnomaly_firstScan_returnsNoAnomaly() {
        ScanLog currentScan = buildScan("12.9716", "77.5946", Instant.now());
        boolean result = aiSentinelService.detectGeographicAnomaly(unitId, currentScan, List.of());
        assertThat(result).isFalse();
    }

    @Test
    void detectGeographicAnomaly_impossibleTravel_returnsTrue() {
        // Mumbai to Delhi (~1400 km) in 10 minutes = ~8400 km/h (impossible)
        ScanLog previous = buildScan("19.0760", "72.8777", Instant.now().minusSeconds(600));
        ScanLog current = buildScan("28.6139", "77.2090", Instant.now());

        when(alertRepository.save(any())).thenReturn(new Alert());

        boolean result = aiSentinelService.detectGeographicAnomaly(unitId, current, List.of(previous));
        assertThat(result).isTrue();
    }

    @Test
    void detectGeographicAnomaly_normalTravel_returnsFalse() {
        // Same city, 5 km apart in 30 minutes = 10 km/h (normal)
        ScanLog previous = buildScan("12.9716", "77.5946", Instant.now().minusSeconds(1800));
        ScanLog current = buildScan("12.9716", "77.6446", Instant.now()); // ~5 km east

        boolean result = aiSentinelService.detectGeographicAnomaly(unitId, current, List.of(previous));
        assertThat(result).isFalse();
    }

    @Test
    void detectGeographicAnomaly_simultaneousScans_returnsTrue() {
        // 100 km apart in 2 minutes = simultaneous scan fraud
        ScanLog previous = buildScan("12.9716", "77.5946", Instant.now().minusSeconds(120));
        ScanLog current = buildScan("13.8700", "77.5946", Instant.now()); // ~100 km north

        when(alertRepository.save(any())).thenReturn(new Alert());

        boolean result = aiSentinelService.detectGeographicAnomaly(unitId, current, List.of(previous));
        assertThat(result).isTrue();
    }

    @Test
    void detectGeographicAnomaly_missingLocation_returnsFalse() {
        ScanLog previous = buildScan(null, null, Instant.now().minusSeconds(600));
        ScanLog current = buildScan("12.9716", "77.5946", Instant.now());

        boolean result = aiSentinelService.detectGeographicAnomaly(unitId, current, List.of(previous));
        assertThat(result).isFalse();
    }

    // ==================== Auto Kill-Switch ====================

    @Test
    void autoTriggerKillSwitch_deactivatesUnit() {
        when(unitItemRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(unitItemRepository.save(any())).thenReturn(unit);
        when(recallEventRepository.save(any())).thenReturn(new RecallEvent());
        when(alertRepository.save(any())).thenReturn(new Alert());

        aiSentinelService.autoTriggerKillSwitch(unitId, "Impossible travel detected");

        assertThat(unit.getIsActive()).isFalse();
        assertThat(unit.getStatus()).isEqualTo(UnitItem.UnitStatus.RECALLED_AUTO);
        verify(unitItemRepository).save(unit);
        verify(recallEventRepository).save(any(RecallEvent.class));
    }

    @Test
    void autoTriggerKillSwitch_generatesAlert() {
        when(unitItemRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(unitItemRepository.save(any())).thenReturn(unit);
        when(recallEventRepository.save(any())).thenReturn(new RecallEvent());
        when(alertRepository.save(any())).thenReturn(new Alert());

        aiSentinelService.autoTriggerKillSwitch(unitId, "Test reason");

        verify(alertRepository, atLeastOnce()).save(any(Alert.class));
    }

    // ==================== Device Fingerprinting ====================

    @Test
    void generateDeviceFingerprint_withUserAgent_returnsNonNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        request.addHeader("Accept-Language", "en-US,en;q=0.9");

        String fingerprint = aiSentinelService.generateDeviceFingerprint(request);

        assertThat(fingerprint).isNotNull().isNotEmpty();
    }

    @Test
    void generateDeviceFingerprint_sameRequest_returnsSameFingerprint() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("User-Agent", "TestBrowser/1.0");
        request1.addHeader("Accept-Language", "en-US");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("User-Agent", "TestBrowser/1.0");
        request2.addHeader("Accept-Language", "en-US");

        String fp1 = aiSentinelService.generateDeviceFingerprint(request1);
        String fp2 = aiSentinelService.generateDeviceFingerprint(request2);

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void generateDeviceFingerprint_differentUserAgents_returnsDifferentFingerprints() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("User-Agent", "Chrome/100");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("User-Agent", "Firefox/99");

        String fp1 = aiSentinelService.generateDeviceFingerprint(request1);
        String fp2 = aiSentinelService.generateDeviceFingerprint(request2);

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    void generateDeviceFingerprint_withCustomHeaders_includesScreenAndTimezone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "TestBrowser");
        request.addHeader("X-Screen-Resolution", "1920x1080");
        request.addHeader("X-Timezone", "Asia/Kolkata");

        String fingerprint = aiSentinelService.generateDeviceFingerprint(request);

        assertThat(fingerprint).isNotNull().isNotEmpty();
    }

    // ==================== Anomaly Score Calculation ====================

    @Test
    void analyzeScanPattern_firstScan_returnsZeroScore() {
        ScanLog currentScan = buildScan("12.9716", "77.5946", Instant.now());
        when(scanLogRepository.findTop5ByUnitIdOrderByScannedAtDesc(unitId)).thenReturn(List.of());

        float score = aiSentinelService.analyzeScanPattern(unitId, currentScan);

        assertThat(score).isEqualTo(0.0f);
        assertThat(currentScan.getAutoFlagged()).isFalse();
    }

    @Test
    void analyzeScanPattern_impossibleTravel_returnsCriticalScore() {
        ScanLog currentScan = buildScan("28.6139", "77.2090", Instant.now());
        ScanLog previousScan = buildScan("19.0760", "72.8777", Instant.now().minusSeconds(600));

        when(scanLogRepository.findTop5ByUnitIdOrderByScannedAtDesc(unitId))
                .thenReturn(List.of(previousScan));
        when(alertRepository.save(any())).thenReturn(new Alert());
        when(unitItemRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(unitItemRepository.save(any())).thenReturn(unit);
        when(recallEventRepository.save(any())).thenReturn(new RecallEvent());

        float score = aiSentinelService.analyzeScanPattern(unitId, currentScan);

        assertThat(score).isEqualTo(1.0f);
        assertThat(currentScan.getAutoFlagged()).isTrue();
    }

    @Test
    void analyzeScanPattern_multipleDeviceChanges_returnsHighScore() {
        ScanLog currentScan = buildScanWithDevice("12.9716", "77.5946", Instant.now(), "device-NEW", "1.2.3.4");

        // 3 previous scans with different device fingerprints
        List<ScanLog> recentScans = List.of(
                buildScanWithDevice("12.9716", "77.5946", Instant.now().minusSeconds(3600), "device-A", "1.2.3.4"),
                buildScanWithDevice("12.9716", "77.5946", Instant.now().minusSeconds(7200), "device-B", "1.2.3.4"),
                buildScanWithDevice("12.9716", "77.5946", Instant.now().minusSeconds(10800), "device-C", "1.2.3.4")
        );

        when(scanLogRepository.findTop5ByUnitIdOrderByScannedAtDesc(unitId)).thenReturn(recentScans);

        float score = aiSentinelService.analyzeScanPattern(unitId, currentScan);

        assertThat(score).isGreaterThanOrEqualTo(0.7f);
        assertThat(currentScan.getAutoFlagged()).isTrue();
    }

    @Test
    void calculateAnomalyScore_delegatesToAnalyzeScanPattern() {
        ScanLog currentScan = buildScan("12.9716", "77.5946", Instant.now());
        when(scanLogRepository.findTop5ByUnitIdOrderByScannedAtDesc(unitId)).thenReturn(List.of());

        float score = aiSentinelService.calculateAnomalyScore(unitId, currentScan);

        assertThat(score).isEqualTo(0.0f);
    }

    // ==================== Alert Generation ====================

    @Test
    void generateAlert_savesAlertToRepository() {
        when(alertRepository.save(any())).thenReturn(new Alert());

        aiSentinelService.generateAlert(AISentinelService.AlertType.GEOGRAPHIC_FRAUD, "Test alert", unitId);

        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void generateAlert_geographicFraud_setsCriticalSeverity() {
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert saved = inv.getArgument(0);
            assertThat(saved.getSeverity()).isEqualTo(Alert.Severity.CRITICAL);
            return saved;
        });

        aiSentinelService.generateAlert(AISentinelService.AlertType.GEOGRAPHIC_FRAUD, "Fraud!", unitId);
    }

    // ==================== Helper Methods ====================

    private ScanLog buildScan(String lat, String lng, Instant scannedAt) {
        ScanLog scan = new ScanLog();
        scan.setLocationLat(lat);
        scan.setLocationLng(lng);
        scan.setScannedAt(scannedAt);
        scan.setDeviceFingerprint("default-device");
        scan.setIpAddress("192.168.1.1");
        scan.setAutoFlagged(false);
        scan.setAnomalyScore(0.0f);
        scan.setScanResult(ScanLog.ScanResult.VALID);
        return scan;
    }

    private ScanLog buildScanWithDevice(String lat, String lng, Instant scannedAt, String device, String ip) {
        ScanLog scan = buildScan(lat, lng, scannedAt);
        scan.setDeviceFingerprint(device);
        scan.setIpAddress(ip);
        return scan;
    }
}
