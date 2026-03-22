package pharmatrust.manufacturing_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pharmatrust.manufacturing_system.config.JwtAuthenticationFilter;
import pharmatrust.manufacturing_system.config.SecurityConfig;
import pharmatrust.manufacturing_system.dto.BatchResponse;
import pharmatrust.manufacturing_system.dto.CreateBatchRequest;
import pharmatrust.manufacturing_system.entity.*;
import pharmatrust.manufacturing_system.repository.*;
import pharmatrust.manufacturing_system.service.BatchApprovalService;
import pharmatrust.manufacturing_system.service.BatchService;
import pharmatrust.manufacturing_system.service.BlockchainService;
import pharmatrust.manufacturing_system.service.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for BatchController
 * Tests: batch listing, quarantine, recall, RBAC enforcement
 * Requirements: FR-001, FR-002, FR-003, FR-019, FR-020
 */
@WebMvcTest(BatchController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BatchService batchService;

    @MockBean
    private BatchApprovalService batchApprovalService;

    @MockBean
    private BatchRepository batchRepository;

    @MockBean
    private UnitItemRepository unitItemRepository;

    @MockBean
    private RecallEventRepository recallEventRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private BlockchainService blockchainService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private Batch batch;
    private UUID batchId;
    private User manufacturer;

    @BeforeEach
    void setUp() {
        batchId = UUID.randomUUID();

        manufacturer = new User();
        manufacturer.setId(UUID.randomUUID());
        manufacturer.setEmail("manufacturer@test.com");
        manufacturer.setRole(User.Role.MANUFACTURER);
        manufacturer.setFullName("Test Manufacturer");

        batch = new Batch();
        batch.setId(batchId);
        batch.setBatchNumber("BATCH-001");
        batch.setMedicineName("Paracetamol 500mg");
        batch.setManufacturingDate(LocalDate.of(2025, 1, 1));
        batch.setExpiryDate(LocalDate.of(2027, 1, 1));
        batch.setStatus(Batch.BatchStatus.ACTIVE);
        batch.setTotalUnits(100);
        batch.setManufacturer(manufacturer);
    }

    // ==================== GET /api/v1/batches ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getAllBatches_authenticated_returnsList() throws Exception {
        BatchResponse batchResponse = BatchResponse.fromEntity(batch);
        when(batchRepository.findAll()).thenReturn(List.of(batch));

        mockMvc.perform(get("/api/v1/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].batchNumber").value("BATCH-001"))
                .andExpect(jsonPath("$[0].medicineName").value("Paracetamol 500mg"));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getAllBatches_withStatusFilter_returnsFilteredList() throws Exception {
        when(batchRepository.findByStatus(Batch.BatchStatus.ACTIVE)).thenReturn(List.of(batch));

        mockMvc.perform(get("/api/v1/batches").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getAllBatches_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/batches").param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /api/v1/batches (legacy) ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void createBatch_manufacturerRole_returns200() throws Exception {
        BatchResponse response = BatchResponse.fromEntity(batch);
        when(batchService.createBatch(any(CreateBatchRequest.class), any())).thenReturn(response);

        CreateBatchRequest request = new CreateBatchRequest();
        request.setMedicineName("Paracetamol 500mg");
        request.setManufacturingDate(LocalDate.of(2025, 1, 1));
        request.setExpiryDate(LocalDate.of(2027, 1, 1));
        request.setTotalUnits(100);

        mockMvc.perform(post("/api/v1/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchNumber").value("BATCH-001"));
    }

    @Test
    @WithMockUser(username = "distributor@test.com", authorities = {"DISTRIBUTOR"})
    void createBatch_nonManufacturerRole_returns403() throws Exception {
        CreateBatchRequest request = new CreateBatchRequest();
        request.setMedicineName("Paracetamol 500mg");
        request.setManufacturingDate(LocalDate.of(2025, 1, 1));
        request.setExpiryDate(LocalDate.of(2027, 1, 1));
        request.setTotalUnits(100);

        mockMvc.perform(post("/api/v1/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createBatch_unauthenticated_returns401or403() throws Exception {
        CreateBatchRequest request = new CreateBatchRequest();
        request.setMedicineName("Paracetamol 500mg");
        request.setManufacturingDate(LocalDate.of(2025, 1, 1));
        request.setExpiryDate(LocalDate.of(2027, 1, 1));
        request.setTotalUnits(100);

        mockMvc.perform(post("/api/v1/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // ==================== POST /api/v1/batches/{id}/quarantine ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void quarantineBatch_existingBatch_returns200() throws Exception {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any())).thenReturn(batch);

        mockMvc.perform(post("/api/v1/batches/{batchId}/quarantine", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Quality issue"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("QUARANTINE"));
    }

    @Test
    @WithMockUser(username = "regulator@test.com", authorities = {"REGULATOR"})
    void quarantineBatch_regulatorRole_returns200() throws Exception {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any())).thenReturn(batch);

        mockMvc.perform(post("/api/v1/batches/{batchId}/quarantine", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "distributor@test.com", authorities = {"DISTRIBUTOR"})
    void quarantineBatch_distributorRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/batches/{batchId}/quarantine", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().is4xxClientError());
    }

    // ==================== POST /api/v1/batches/{id}/recall ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void recallBatch_existingBatch_returns200WithUnitsAffected() throws Exception {
        UnitItem unit1 = new UnitItem();
        unit1.setId(UUID.randomUUID());
        unit1.setStatus(UnitItem.UnitStatus.ACTIVE);
        unit1.setIsActive(true);

        UnitItem unit2 = new UnitItem();
        unit2.setId(UUID.randomUUID());
        unit2.setStatus(UnitItem.UnitStatus.ACTIVE);
        unit2.setIsActive(true);

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any())).thenReturn(batch);
        when(unitItemRepository.findByBatchId(batchId)).thenReturn(List.of(unit1, unit2));
        when(unitItemRepository.saveAll(any())).thenReturn(List.of(unit1, unit2));
        when(userRepository.findByEmail("manufacturer@test.com")).thenReturn(Optional.of(manufacturer));
        when(recallEventRepository.save(any())).thenReturn(new RecallEvent());
        when(blockchainService.emitRecallEvent(any(), any(), any(), eq(false))).thenReturn("0xTXHASH");

        Map<String, String> request = Map.of("reason", "Contamination detected");

        mockMvc.perform(post("/api/v1/batches/{batchId}/recall", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("RECALLED"))
                .andExpect(jsonPath("$.unitsAffected").value(2))
                .andExpect(jsonPath("$.reason").value("Contamination detected"));
    }

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void recallBatch_notFound_returns400() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(batchRepository.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/batches/{batchId}/recall", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Test"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "patient@test.com", authorities = {"PATIENT"})
    void recallBatch_patientRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/batches/{batchId}/recall", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Test"))))
                .andExpect(status().is4xxClientError());
    }

    // ==================== GET /api/v1/batches/pending-approvals ====================

    @Test
    @WithMockUser(username = "manufacturer@test.com", authorities = {"MANUFACTURER"})
    void getPendingApprovals_manufacturerRole_returnsList() throws Exception {
        batch.setStatus(Batch.BatchStatus.PENDING_APPROVAL);
        when(batchRepository.findByStatus(Batch.BatchStatus.PENDING_APPROVAL)).thenReturn(List.of(batch));
        when(batchApprovalService.getBatchApprovals(batchId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/batches/pending-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].batchNumber").value("BATCH-001"))
                .andExpect(jsonPath("$[0].requiredApprovals").value(2));
    }

    @Test
    @WithMockUser(username = "distributor@test.com", authorities = {"DISTRIBUTOR"})
    void getPendingApprovals_distributorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/batches/pending-approvals"))
                .andExpect(status().is4xxClientError());
    }
}
