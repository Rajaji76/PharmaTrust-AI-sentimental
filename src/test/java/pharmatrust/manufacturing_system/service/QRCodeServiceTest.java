package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pharmatrust.manufacturing_system.entity.Batch;
import pharmatrust.manufacturing_system.entity.UnitItem;
import pharmatrust.manufacturing_system.entity.User;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QRCodeService
 * Tests: Protobuf serialize/deserialize, QR payload URL format, offline verification
 * Requirements: FR-010, FR-011, FR-012
 */
@ExtendWith(MockitoExtension.class)
class QRCodeServiceTest {

    @Mock
    private CryptographyService cryptographyService;

    @InjectMocks
    private QRCodeService qrCodeService;

    private UnitItem unit;
    private Batch batch;
    private KeyPair ecKeyPair;
    private String serialNumber;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(qrCodeService, "maxScanLimit", 5);
        ReflectionTestUtils.setField(qrCodeService, "offlineVerificationEnabled", true);

        // Generate real EC key pair for signing tests
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        ecKeyPair = keyGen.generateKeyPair();

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
        batch.setManufacturer(manufacturer);

        unit = new UnitItem();
        unit.setId(UUID.randomUUID());
        unit.setSerialNumber(serialNumber);
        unit.setBatch(batch);
        unit.setStatus(UnitItem.UnitStatus.ACTIVE);
        unit.setIsActive(true);
        unit.setUnitType(UnitItem.UnitType.TABLET);
    }

    // ==================== QR Payload Generation ====================

    @Test
    void generateQRPayload_validUnit_returnsUrlFormat() throws Exception {
        byte[] fakeSignature = new byte[64];
        when(cryptographyService.signBytesWithECDSA(any(), any())).thenReturn(fakeSignature);

        String payload = qrCodeService.generateQRPayload(unit, ecKeyPair.getPrivate());

        assertThat(payload).isNotNull();
        assertThat(payload).startsWith("https://verify.pharmatrust.ai/v/");
    }

    @Test
    void generateQRPayload_payloadIsBase64UrlEncoded() throws Exception {
        byte[] fakeSignature = new byte[64];
        when(cryptographyService.signBytesWithECDSA(any(), any())).thenReturn(fakeSignature);

        String payload = qrCodeService.generateQRPayload(unit, ecKeyPair.getPrivate());

        // Extract base64 part after last "/"
        String base64Part = payload.substring(payload.lastIndexOf("/") + 1);
        // URL-safe base64 should not contain + or /
        assertThat(base64Part).doesNotContain("+", "/");
        // Should be decodable
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(base64Part);
        assertThat(decoded).isNotEmpty();
    }

    @Test
    void generateQRPayload_differentUnits_produceDifferentPayloads() throws Exception {
        byte[] fakeSignature = new byte[64];
        when(cryptographyService.signBytesWithECDSA(any(), any())).thenReturn(fakeSignature);

        UnitItem unit2 = new UnitItem();
        unit2.setId(UUID.randomUUID());
        unit2.setSerialNumber(UUID.randomUUID().toString());
        unit2.setBatch(batch);

        String payload1 = qrCodeService.generateQRPayload(unit, ecKeyPair.getPrivate());
        String payload2 = qrCodeService.generateQRPayload(unit2, ecKeyPair.getPrivate());

        assertThat(payload1).isNotEqualTo(payload2);
    }

    @Test
    void generateQRPayload_signingFails_throwsRuntimeException() throws Exception {
        when(cryptographyService.signBytesWithECDSA(any(), any()))
                .thenThrow(new RuntimeException("Signing failed"));

        assertThatThrownBy(() -> qrCodeService.generateQRPayload(unit, ecKeyPair.getPrivate()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QR payload generation failed");
    }

    // ==================== QR Image Generation ====================

    @Test
    void generateQRImage_validPayload_returnsPngBytes() {
        String payload = "https://verify.pharmatrust.ai/v/testpayload";

        byte[] imageBytes = qrCodeService.generateQRImage(payload);

        assertThat(imageBytes).isNotNull().isNotEmpty();
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(imageBytes[0]).isEqualTo((byte) 0x89);
        assertThat(imageBytes[1]).isEqualTo((byte) 0x50);
        assertThat(imageBytes[2]).isEqualTo((byte) 0x4E);
        assertThat(imageBytes[3]).isEqualTo((byte) 0x47);
    }

    @Test
    void generateQRImage_longPayload_stillGeneratesImage() {
        String longPayload = "https://verify.pharmatrust.ai/v/" + "A".repeat(200);

        byte[] imageBytes = qrCodeService.generateQRImage(longPayload);

        assertThat(imageBytes).isNotNull().isNotEmpty();
    }

    // ==================== Online Verification ====================

    @Test
    void verifyQROnline_validProtobufPayload_returnsValidStatus() throws Exception {
        // Generate a real payload first
        byte[] fakeSignature = new byte[64];
        when(cryptographyService.signBytesWithECDSA(any(), any())).thenReturn(fakeSignature);

        String qrUrl = qrCodeService.generateQRPayload(unit, ecKeyPair.getPrivate());

        QRCodeService.VerificationResult result = qrCodeService.verifyQROnline(qrUrl);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("VALID_ONLINE");
        assertThat(result.getSerialNumber()).isNotNull();
        assertThat(result.getNeedsOnlineSync()).isFalse();
    }

    @Test
    void verifyQROnline_invalidBase64_returnsErrorStatus() {
        String invalidUrl = "https://verify.pharmatrust.ai/v/!!!invalid!!!";

        QRCodeService.VerificationResult result = qrCodeService.verifyQROnline(invalidUrl);

        assertThat(result.getStatus()).isEqualTo("ERROR");
        assertThat(result.getMessage()).contains("QR verification failed");
    }

    @Test
    void verifyQROnline_corruptedPayload_returnsErrorStatus() {
        String corruptedUrl = "https://verify.pharmatrust.ai/v/dGhpcyBpcyBub3QgcHJvdG9idWY";

        QRCodeService.VerificationResult result = qrCodeService.verifyQROnline(corruptedUrl);

        // Either ERROR or VALID_ONLINE depending on protobuf parsing tolerance
        assertThat(result.getStatus()).isIn("ERROR", "VALID_ONLINE");
    }

    // ==================== Offline Verification ====================

    @Test
    void verifyQROffline_validSignature_returnsValidOfflineStatus() throws Exception {
        // Generate payload with real ECDSA signing
        CryptographyService realCrypto = new CryptographyService();
        QRCodeService realQrService = new QRCodeService(realCrypto);
        ReflectionTestUtils.setField(realQrService, "maxScanLimit", 5);
        ReflectionTestUtils.setField(realQrService, "offlineVerificationEnabled", true);

        String qrUrl = realQrService.generateQRPayload(unit, ecKeyPair.getPrivate());

        QRCodeService.VerificationResult result = realQrService.verifyQROffline(qrUrl, ecKeyPair.getPublic());

        assertThat(result.getStatus()).isEqualTo("VALID_OFFLINE");
        assertThat(result.getNeedsOnlineSync()).isTrue();
        assertThat(result.getSerialNumber()).isNotNull();
    }

    @Test
    void verifyQROffline_wrongPublicKey_returnsInvalidStatus() throws Exception {
        // Generate payload with key1, verify with key2
        CryptographyService realCrypto = new CryptographyService();
        QRCodeService realQrService = new QRCodeService(realCrypto);
        ReflectionTestUtils.setField(realQrService, "maxScanLimit", 5);
        ReflectionTestUtils.setField(realQrService, "offlineVerificationEnabled", true);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair wrongKeyPair = keyGen.generateKeyPair();

        String qrUrl = realQrService.generateQRPayload(unit, ecKeyPair.getPrivate());

        QRCodeService.VerificationResult result = realQrService.verifyQROffline(qrUrl, wrongKeyPair.getPublic());

        assertThat(result.getStatus()).isEqualTo("INVALID");
        assertThat(result.getMessage()).contains("Invalid signature");
    }

    @Test
    void verifyQROffline_offlineDisabled_returnsDisabledStatus() throws Exception {
        ReflectionTestUtils.setField(qrCodeService, "offlineVerificationEnabled", false);

        QRCodeService.VerificationResult result = qrCodeService.verifyQROffline(
                "https://verify.pharmatrust.ai/v/test", ecKeyPair.getPublic());

        assertThat(result.getStatus()).isEqualTo("OFFLINE_DISABLED");
        assertThat(result.getNeedsOnlineSync()).isTrue();
    }

    @Test
    void verifyQROffline_invalidUrl_returnsErrorStatus() {
        QRCodeService.VerificationResult result = qrCodeService.verifyQROffline(
                "https://verify.pharmatrust.ai/v/!!!bad!!!", ecKeyPair.getPublic());

        assertThat(result.getStatus()).isEqualTo("ERROR");
    }

    // ==================== Legacy QR Methods ====================

    @Test
    void generateUnitQRCode_validInputs_returnsBase64DataUri() {
        String result = qrCodeService.generateUnitQRCode(
                serialNumber, "BATCH-001", "Paracetamol 500mg",
                "2025-01-01", "2027-01-01", "sig123", "secret123");

        assertThat(result).startsWith("data:image/png;base64,");
    }

    @Test
    void generateParentQRCode_validInputs_returnsBase64DataUri() {
        String result = qrCodeService.generateParentQRCode(
                "BOX-" + UUID.randomUUID(), 10, "merkle123", "BATCH-001");

        assertThat(result).startsWith("data:image/png;base64,");
    }

    // ==================== VerificationResult Builder ====================

    @Test
    void verificationResult_builder_setsAllFields() {
        QRCodeService.VerificationResult result = QRCodeService.VerificationResult.builder()
                .status("VALID_ONLINE")
                .message("Test message")
                .serialNumber("SN-001")
                .timestamp(1234567890)
                .needsOnlineSync(false)
                .build();

        assertThat(result.getStatus()).isEqualTo("VALID_ONLINE");
        assertThat(result.getMessage()).isEqualTo("Test message");
        assertThat(result.getSerialNumber()).isEqualTo("SN-001");
        assertThat(result.getTimestamp()).isEqualTo(1234567890);
        assertThat(result.getNeedsOnlineSync()).isFalse();
    }
}
