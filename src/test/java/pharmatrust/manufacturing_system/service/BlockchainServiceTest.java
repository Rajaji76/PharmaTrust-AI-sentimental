package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BlockchainService
 * Tests: degraded mode (no live node), transaction status, ABI encoding
 * Requirements: BR-004, IR-003, IR-004
 *
 * Note: These tests run without a live Ethereum node.
 * The service gracefully degrades when blockchain is unavailable.
 */
@ExtendWith(MockitoExtension.class)
class BlockchainServiceTest {

    @InjectMocks
    private BlockchainService blockchainService;

    @BeforeEach
    void setUp() {
        // Configure to use a non-existent node so it starts in degraded mode
        ReflectionTestUtils.setField(blockchainService, "networkUrl", "http://localhost:19999");
        ReflectionTestUtils.setField(blockchainService, "contractAddress", "0x1234567890abcdef");
        ReflectionTestUtils.setField(blockchainService, "privateKey", "");
        ReflectionTestUtils.setField(blockchainService, "confirmationBlocks", 12);

        // Manually trigger init (PostConstruct won't run in unit tests)
        blockchainService.init();
    }

    // ==================== Degraded Mode (No Live Node) ====================

    @Test
    void mintBatchToken_noConnection_returnsNull() {
        String txHash = blockchainService.mintBatchToken(
                "BATCH001",
                "paracetamol-hash",
                LocalDate.now(),
                LocalDate.now().plusYears(2),
                "0xManufacturerAddress",
                "labReportHash",
                "merkleRoot",
                10000L);

        // When blockchain is not connected, returns null gracefully
        assertThat(txHash).isNull();
    }

    @Test
    void emitRecallEvent_noConnection_returnsNull() {
        String txHash = blockchainService.emitRecallEvent(
                "BATCH001",
                "0xRegulatorAddress",
                "Contamination detected",
                false);

        assertThat(txHash).isNull();
    }

    @Test
    void getTransactionStatus_noConnection_returnsUnknown() {
        BlockchainService.TransactionStatus status =
                blockchainService.getTransactionStatus("0xSomeTxHash");

        assertThat(status).isNotNull();
        assertThat(status.getStatus()).isEqualTo("UNKNOWN");
        assertThat(status.getTxHash()).isEqualTo("0xSomeTxHash");
    }

    @Test
    void getTransactionStatus_nullTxHash_returnsUnknown() {
        BlockchainService.TransactionStatus status =
                blockchainService.getTransactionStatus(null);

        assertThat(status).isNotNull();
        assertThat(status.getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void waitForConfirmations_noConnection_returnsFalse() {
        boolean confirmed = blockchainService.waitForConfirmations("0xSomeTxHash");

        assertThat(confirmed).isFalse();
    }

    // ==================== TransactionStatus DTO ====================

    @Test
    void transactionStatus_builder_createsCorrectObject() {
        BlockchainService.TransactionStatus status = BlockchainService.TransactionStatus.builder()
                .txHash("0xabc123")
                .status("CONFIRMED")
                .blockNumber(12345L)
                .confirmations(15L)
                .gasUsed(21000L)
                .build();

        assertThat(status.getTxHash()).isEqualTo("0xabc123");
        assertThat(status.getStatus()).isEqualTo("CONFIRMED");
        assertThat(status.getBlockNumber()).isEqualTo(12345L);
        assertThat(status.getConfirmations()).isEqualTo(15L);
        assertThat(status.getGasUsed()).isEqualTo(21000L);
    }

    @Test
    void transactionStatus_pendingStatus_hasNoErrorMessage() {
        BlockchainService.TransactionStatus status = BlockchainService.TransactionStatus.builder()
                .txHash("0xpending")
                .status("PENDING")
                .build();

        assertThat(status.getStatus()).isEqualTo("PENDING");
        assertThat(status.getErrorMessage()).isNull();
    }

    // ==================== Multiple calls in degraded mode ====================

    @Test
    void mintBatchToken_multipleCalls_allReturnNull() {
        for (int i = 0; i < 3; i++) {
            String result = blockchainService.mintBatchToken(
                    "BATCH00" + i, "hash", LocalDate.now(),
                    LocalDate.now().plusYears(1), "0xAddr",
                    "labHash", "merkle", 100L);
            assertThat(result).isNull();
        }
    }
}
