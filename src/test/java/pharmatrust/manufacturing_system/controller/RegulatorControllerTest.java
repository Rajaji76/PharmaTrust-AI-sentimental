package pharmatrust.manufacturing_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pharmatrust.manufacturing_system.config.JwtAuthenticationFilter;
import pharmatrust.manufacturing_system.config.SecurityConfig;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;
import pharmatrust.manufacturing_system.service.BlockchainService;
import pharmatrust.manufacturing_system.service.HierarchyKillSwitchService;
import pharmatrust.manufacturing_system.service.JwtService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for RegulatorController
 * Tests: access control, privacy protection, fraud alerts, recalls
 * Requirements: FR-003, NFR-009
 */
@WebMvcTest(RegulatorController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RegulatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertRepository alertRepository;
    @MockBean
    private RecallEventRepository recallEventRepository;
    @MockBean
    private BatchRepository batchRepository;
    @MockBean
    private AuditLogRepository auditLogRepository;
    @MockBean
    private HierarchyKillSwitchService hierarchyKillSwitchService;
    @MockBean
    private UnitItemRepository unitItemRepository;
    @MockBean
    private ScanLogRepository scanLogRepository;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private BlockchainService blockchainService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private Alert testAlert;
    private RecallEvent testRecall;
    private Batch testBatch;

    @BeforeEach
    void setUp() {
        User manufacturer = new User();
        manufacturer.setId(UUID.randomUUID());
        manufacturer.setEmail("mfg@pharma.com");
        manufacturer.setRole(User.Role.MANUFACTURER);

        testBatch = new Batch();
        testBatch.setId(UUID.randomUUID());
        testBatch.setBatchNumber("BATCH001");
        testBatch.setMedicineName("Paracetamol");
        testBatch.setStatus(Batch.BatchStatus.ACTIVE);
        testBatch.setExpiryDate(LocalDate.now().plusYears(2));
        testBatch.setManufacturingDate(LocalDate.now());
        testBatch.setManufacturer(manufacturer);

        testAlert = new Alert();
        testAlert.setId(UUID.randomUUID());
        testAlert.setAlertType(Alert.AlertType.GEOGRAPHIC_FRAUD);
        testAlert.setSeverity(Alert.Severity.CRITICAL);
        testAlert.setMessage("Impossible travel detected");
        testAlert.setCreatedAt(Instant.now());
        testAlert.setAcknowledged(false);

        User initiator = new User();
        initiator.setId(UUID.randomUUID());
        initiator.setEmail("reg@pharma.com");

        testRecall = new RecallEvent();
        testRecall.setId(UUID.randomUUID());
        testRecall.setBatch(testBatch);
        testRecall.setInitiatedBy(initiator);
        testRecall.setRecallType(RecallEvent.RecallType.MANUAL);
        testRecall.setReason("Contamination");
        testRecall.setStatus(RecallEvent.RecallStatus.ACTIVE);
        testRecall.setInitiatedAt(Instant.now());
    }

    // ==================== Access Control ====================

    @Test
    void alerts_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/regulator/alerts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(authorities = "MANUFACTURER")
    void alerts_withManufacturerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/regulator/alerts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(authorities = "DISTRIBUTOR")
    void alerts_withDistributorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/regulator/alerts"))
                .andExpect(status().is4xxClientError());
    }

    // ==================== Fraud Alerts ====================

    @Test
    @WithMockUser(authorities = "REGULATOR")
    void getFraudAlerts_withRegulatorRole_returns200() throws Exception {
        when(alertRepository.findFraudAlerts()).thenReturn(List.of(testAlert));

        mockMvc.perform(get("/api/v1/regulator/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlerts").value(1))
                .andExpect(jsonPath("$.alerts[0].type").value("GEOGRAPHIC_FRAUD"))
                .andExpect(jsonPath("$.alerts[0].severity").value("CRITICAL"));
    }

    @Test
    @WithMockUser(authorities = "REGULATOR")
    void getFraudAlerts_noAlerts_returnsEmptyList() throws Exception {
        when(alertRepository.findFraudAlerts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/regulator/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlerts").value(0));
    }

    // ==================== Recall Events ====================

    @Test
    @WithMockUser(authorities = "REGULATOR")
    void getRecallEvents_withRegulatorRole_returns200() throws Exception {
        when(recallEventRepository.findByStatusOrderByInitiatedAtDesc(RecallEvent.RecallStatus.ACTIVE))
                .thenReturn(List.of(testRecall));

        mockMvc.perform(get("/api/v1/regulator/recalls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecalls").value(1))
                .andExpect(jsonPath("$.recalls[0].batchNumber").value("BATCH001"))
                .andExpect(jsonPath("$.recalls[0].reason").value("Contamination"));
    }

    // ==================== Flagged Batches ====================

    @Test
    @WithMockUser(authorities = "REGULATOR")
    void getFlaggedBatches_returns200WithPrivacyProtection() throws Exception {
        when(batchRepository.findByStatus(Batch.BatchStatus.RECALLED)).thenReturn(List.of(testBatch));
        when(batchRepository.findByStatus(Batch.BatchStatus.RECALLED_AUTO)).thenReturn(List.of());
        when(batchRepository.findByStatus(Batch.BatchStatus.QUARANTINE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/regulator/flagged-batches"))
                .andExpect(status().isOk())
                // Privacy: should NOT expose totalUnits (business metric)
                .andExpect(jsonPath("$.recalled[0].batchNumber").value("BATCH001"))
                .andExpect(jsonPath("$.recalled[0].medicineName").value("Paracetamol"))
                // Should NOT expose production volumes
                .andExpect(jsonPath("$.recalled[0].totalUnits").doesNotExist());
    }

    // ==================== Audit Logs ====================

    @Test
    @WithMockUser(authorities = "REGULATOR")
    void getAuditLogs_returns200() throws Exception {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("BATCH_CREATED");
        log.setEntityType("BATCH");
        log.setCreatedAt(Instant.now());
        log.setIpAddress("192.168.1.1");

        when(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/regulator/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs").value(1))
                .andExpect(jsonPath("$.logs[0].action").value("BATCH_CREATED"));
    }

    // ==================== Kill Switch ====================

    @Test
    @WithMockUser(authorities = "REGULATOR", username = "regulator@pharma.com")
    void killSwitch_validSerial_returns200() throws Exception {
        when(hierarchyKillSwitchService.killParentAndChildren(any(), any())).thenReturn(5);

        mockMvc.perform(post("/api/v1/regulator/kill-switch")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"SN-001\",\"reason\":\"Contamination\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.blockedUnits").value(5));
    }

    @Test
    @WithMockUser(authorities = "MANUFACTURER")
    void killSwitch_withManufacturerRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/regulator/kill-switch")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"SN-001\"}"))
                .andExpect(status().is4xxClientError());
    }

    // ==================== Stats ====================

    @Test
    @WithMockUser(authorities = "REGULATOR")
    void getStats_returns200() throws Exception {
        when(unitItemRepository.count()).thenReturn(10000L);
        when(unitItemRepository.countByStatus(UnitItem.UnitStatus.ACTIVE)).thenReturn(9500L);
        when(unitItemRepository.countByStatus(UnitItem.UnitStatus.RECALLED)).thenReturn(100L);
        when(unitItemRepository.countFlaggedUnits()).thenReturn(50L);
        when(alertRepository.countByAcknowledgedFalse()).thenReturn(5L);
        when(unitItemRepository.countByCurrentOwnerIsNull()).thenReturn(3000L);
        when(unitItemRepository.countByCurrentOwnerRole(any())).thenReturn(0L);

        mockMvc.perform(get("/api/v1/regulator/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(10000))
                .andExpect(jsonPath("$.activeUnits").value(9500))
                .andExpect(jsonPath("$.recalledUnits").value(100));
    }
}
