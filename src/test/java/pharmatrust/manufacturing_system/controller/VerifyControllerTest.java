package pharmatrust.manufacturing_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pharmatrust.manufacturing_system.config.JwtAuthenticationFilter;
import pharmatrust.manufacturing_system.config.SecurityConfig;
import pharmatrust.manufacturing_system.dto.ScanRequest;
import pharmatrust.manufacturing_system.dto.ScanResponse;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.ScanLogRepository;
import pharmatrust.manufacturing_system.repository.UnitItemRepository;
import pharmatrust.manufacturing_system.service.JwtService;
import pharmatrust.manufacturing_system.service.QRCodeService;
import pharmatrust.manufacturing_system.service.ScanService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for VerifyController
 * Tests: QR scan endpoints, unit details, scan history
 * Requirements: FR-010, FR-012, FR-013
 */
@WebMvcTest(VerifyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VerifyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScanService scanService;

    @MockBean
    private QRCodeService qrCodeService;

    @MockBean
    private UnitItemRepository unitItemRepository;

    @MockBean
    private ScanLogRepository scanLogRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private UnitItem unit;
    private Batch batch;
    private String serialNumber;

    @BeforeEach
    void setUp() {
        serialNumber = UUID.randomUUID().toString();

        User manufacturer = new User();
        manufacturer.setId(UUID.randomUUID());
        manufacturer.setFullName("Test Manufacturer");

        batch = new Batch();
        batch.setId(UUID.randomUUID());
        batch.setBatchNumber("BATCH-001");
        batch.setMedicineName("Paracetamol 500mg");
        batch.setManufacturingDate(LocalDate.of(2025, 1, 1));
        batch.setExpiryDate(LocalDate.of(2027, 1, 1));
        batch.setStatus(Batch.BatchStatus.ACTIVE);
        batch.setManufacturer(manufacturer);

        unit = UnitItem.builder()
                .id(UUID.randomUUID())
                .serialNumber(serialNumber)
                .batch(batch)
                .status(UnitItem.UnitStatus.ACTIVE)
                .isActive(true)
                .scanCount(2)
                .maxScanLimit(5)
                .unitType(UnitItem.UnitType.TABLET)
                .build();
    }

    // ==================== POST /api/v1/verify/scan ====================

    @Test
    void verifyScan_validSerial_returns200() throws Exception {
        ScanResponse scanResponse = new ScanResponse();
        scanResponse.setIsValid(true);
        scanResponse.setMedicineName("Paracetamol 500mg");
        scanResponse.setBatchNumber("BATCH-001");
        scanResponse.setMessage("Valid medicine unit");

        when(scanService.scanUnit(any(ScanRequest.class))).thenReturn(scanResponse);

        Map<String, String> request = Map.of("serialNumber", serialNumber);

        mockMvc.perform(post("/api/v1/verify/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.medicineName").value("Paracetamol 500mg"));
    }

    @Test
    void verifyScan_recalledUnit_returnsRecalledFlag() throws Exception {
        ScanResponse scanResponse = new ScanResponse();
        scanResponse.setIsValid(false);
        scanResponse.setIsRecalled(true);
        scanResponse.setMessage("Unit has been recalled");

        when(scanService.scanUnit(any(ScanRequest.class))).thenReturn(scanResponse);

        Map<String, String> request = Map.of("serialNumber", serialNumber);

        mockMvc.perform(post("/api/v1/verify/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRecalled").value(true));
    }

    // ==================== POST /api/v1/verify/offline ====================

    @Test
    void verifyOffline_validSerial_returnsOfflineFlag() throws Exception {
        ScanResponse scanResponse = new ScanResponse();
        scanResponse.setIsValid(true);
        scanResponse.setMedicineName("Paracetamol 500mg");
        scanResponse.setBatchNumber("BATCH-001");
        scanResponse.setMessage("Valid");

        when(scanService.scanUnit(any(ScanRequest.class))).thenReturn(scanResponse);

        Map<String, String> request = Map.of("serialNumber", serialNumber, "totpCode", "123456");

        mockMvc.perform(post("/api/v1/verify/offline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offlineMode").value(true))
                .andExpect(jsonPath("$.isValid").value(true));
    }

    // ==================== GET /api/v1/verify/unit/{serial} ====================

    @Test
    void getUnitDetails_existingSerial_returns200WithDetails() throws Exception {
        when(unitItemRepository.findBySerialNumber(serialNumber)).thenReturn(Optional.of(unit));

        mockMvc.perform(get("/api/v1/verify/unit/{serial}", serialNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value(serialNumber))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.batchNumber").value("BATCH-001"))
                .andExpect(jsonPath("$.medicineName").value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.scanCount").value(2))
                .andExpect(jsonPath("$.maxScanLimit").value(5));
    }

    @Test
    void getUnitDetails_unknownSerial_returns404() throws Exception {
        when(unitItemRepository.findBySerialNumber("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/verify/unit/{serial}", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /api/v1/verify/history/{serial} ====================

    @Test
    void getScanHistory_existingUnit_returnsHistory() throws Exception {
        ScanLog log1 = new ScanLog();
        log1.setScannedAt(Instant.now().minusSeconds(3600));
        log1.setScanResult(ScanLog.ScanResult.VALID);
        log1.setLocationLat("12.9716");
        log1.setLocationLng("77.5946");
        log1.setAnomalyScore(0.0f);
        log1.setAutoFlagged(false);

        when(unitItemRepository.findBySerialNumber(serialNumber)).thenReturn(Optional.of(unit));
        when(scanLogRepository.findByUnitIdOrderByScannedAtDesc(unit.getId())).thenReturn(List.of(log1));

        mockMvc.perform(get("/api/v1/verify/history/{serial}", serialNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value(serialNumber))
                .andExpect(jsonPath("$.totalScans").value(1))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void getScanHistory_noScans_returnsEmptyHistory() throws Exception {
        when(unitItemRepository.findBySerialNumber(serialNumber)).thenReturn(Optional.of(unit));
        when(scanLogRepository.findByUnitIdOrderByScannedAtDesc(unit.getId())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/verify/history/{serial}", serialNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScans").value(0))
                .andExpect(jsonPath("$.history").isEmpty());
    }

    @Test
    void getScanHistory_unknownSerial_returns404() throws Exception {
        when(unitItemRepository.findBySerialNumber("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/verify/history/{serial}", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /api/v1/verify/{serial} ====================

    @Test
    void getUnitDetailsBySerial_existingUnit_returns200() throws Exception {
        when(unitItemRepository.findBySerialNumber(serialNumber)).thenReturn(Optional.of(unit));

        mockMvc.perform(get("/api/v1/verify/{serial}", serialNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value(serialNumber))
                .andExpect(jsonPath("$.medicineName").value("Paracetamol 500mg"));
    }

    @Test
    void getUnitDetailsBySerial_unknownSerial_returns404() throws Exception {
        when(unitItemRepository.findBySerialNumber("NOTFOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/verify/{serial}", "NOTFOUND"))
                .andExpect(status().isNotFound());
    }
}
