package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CryptographyService
 * Tests: SHA-256 hashing, RSA/ECDSA key generation, signatures, HMAC, Merkle root
 * Requirements: FR-004, FR-010, FR-011, NFR-007, NFR-021
 */
class CryptographyServiceTest {

    private CryptographyService cryptographyService;

    @BeforeEach
    void setUp() {
        cryptographyService = new CryptographyService();
    }

    // ==================== SHA-256 Hashing ====================

    @Test
    void generateSHA256Hash_bytes_returnsHexString() {
        byte[] data = "hello world".getBytes();
        String hash = cryptographyService.generateSHA256Hash(data);

        assertThat(hash).isNotNull().hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
        assertThat(hash).matches("[a-f0-9]+");
    }

    @Test
    void generateSHA256Hash_string_returnsSameAsBytes() {
        String data = "pharmatrust-test";
        String hashFromString = cryptographyService.generateSHA256Hash(data);
        String hashFromBytes = cryptographyService.generateSHA256Hash(data.getBytes());

        assertThat(hashFromString).isEqualTo(hashFromBytes);
    }

    @Test
    void generateSHA256Hash_sameInput_returnsSameHash() {
        String data = "batch-ABC123";
        String hash1 = cryptographyService.generateSHA256Hash(data);
        String hash2 = cryptographyService.generateSHA256Hash(data);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void generateSHA256Hash_differentInputs_returnsDifferentHashes() {
        String hash1 = cryptographyService.generateSHA256Hash("batch-001");
        String hash2 = cryptographyService.generateSHA256Hash("batch-002");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void generateSHA256Hash_emptyString_returnsKnownHash() {
        // SHA-256 of empty string is well-known
        String hash = cryptographyService.generateSHA256Hash("");
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    // ==================== RSA Key Generation ====================

    @Test
    void generateRSAKeyPair_returnsValidKeyPair() {
        KeyPair keyPair = cryptographyService.generateRSAKeyPair();

        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();
        assertThat(keyPair.getPublic().getAlgorithm()).isEqualTo("RSA");
        assertThat(keyPair.getPrivate().getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void generateRSAKeyPair_eachCallProducesDifferentKeys() {
        KeyPair kp1 = cryptographyService.generateRSAKeyPair();
        KeyPair kp2 = cryptographyService.generateRSAKeyPair();

        assertThat(kp1.getPublic().getEncoded())
                .isNotEqualTo(kp2.getPublic().getEncoded());
    }

    // ==================== ECDSA Key Generation ====================

    @Test
    void generateECDSAKeyPair_returnsValidKeyPair() {
        KeyPair keyPair = cryptographyService.generateECDSAKeyPair();

        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();
        assertThat(keyPair.getPublic().getAlgorithm()).isEqualTo("EC");
    }

    // ==================== RSA Sign & Verify ====================

    @Test
    void signAndVerifyRSA_validSignature_returnsTrue() {
        KeyPair keyPair = cryptographyService.generateRSAKeyPair();
        String data = "batch-data-to-sign";

        String signature = cryptographyService.signDataWithRSA(data, keyPair.getPrivate());
        boolean valid = cryptographyService.verifyRSASignature(data, signature, keyPair.getPublic());

        assertThat(valid).isTrue();
    }

    @Test
    void verifyRSASignature_tamperedData_returnsFalse() {
        KeyPair keyPair = cryptographyService.generateRSAKeyPair();
        String originalData = "original-batch-data";
        String tamperedData = "tampered-batch-data";

        String signature = cryptographyService.signDataWithRSA(originalData, keyPair.getPrivate());
        boolean valid = cryptographyService.verifyRSASignature(tamperedData, signature, keyPair.getPublic());

        assertThat(valid).isFalse();
    }

    @Test
    void verifyRSASignature_wrongPublicKey_returnsFalse() {
        KeyPair kp1 = cryptographyService.generateRSAKeyPair();
        KeyPair kp2 = cryptographyService.generateRSAKeyPair();
        String data = "test-data";

        String signature = cryptographyService.signDataWithRSA(data, kp1.getPrivate());
        boolean valid = cryptographyService.verifyRSASignature(data, signature, kp2.getPublic());

        assertThat(valid).isFalse();
    }

    // ==================== ECDSA Sign & Verify ====================

    @Test
    void signAndVerifyECDSA_validSignature_returnsTrue() {
        KeyPair keyPair = cryptographyService.generateECDSAKeyPair();
        String data = "qr-payload-data";

        String signature = cryptographyService.signDataWithECDSA(data, keyPair.getPrivate());
        boolean valid = cryptographyService.verifyECDSASignature(data, signature, keyPair.getPublic());

        assertThat(valid).isTrue();
    }

    @Test
    void signAndVerifyECDSABytes_validSignature_returnsTrue() {
        KeyPair keyPair = cryptographyService.generateECDSAKeyPair();
        byte[] data = "protobuf-payload".getBytes();

        byte[] signature = cryptographyService.signBytesWithECDSA(data, keyPair.getPrivate());
        boolean valid = cryptographyService.verifyECDSASignatureBytes(data, signature, keyPair.getPublic());

        assertThat(valid).isTrue();
    }

    @Test
    void verifyECDSASignature_tamperedData_returnsFalse() {
        KeyPair keyPair = cryptographyService.generateECDSAKeyPair();
        String signature = cryptographyService.signDataWithECDSA("original", keyPair.getPrivate());

        boolean valid = cryptographyService.verifyECDSASignature("tampered", signature, keyPair.getPublic());

        assertThat(valid).isFalse();
    }

    // ==================== Key Encoding/Decoding ====================

    @Test
    void encodeAndDecodeRSAPublicKey_roundTrip() {
        KeyPair keyPair = cryptographyService.generateRSAKeyPair();
        PublicKey original = keyPair.getPublic();

        String encoded = cryptographyService.encodePublicKey(original);
        PublicKey decoded = cryptographyService.decodeRSAPublicKey(encoded);

        assertThat(decoded.getEncoded()).isEqualTo(original.getEncoded());
    }

    @Test
    void encodeAndDecodeRSAPrivateKey_roundTrip() {
        KeyPair keyPair = cryptographyService.generateRSAKeyPair();
        PrivateKey original = keyPair.getPrivate();

        String encoded = cryptographyService.encodePrivateKey(original);
        PrivateKey decoded = cryptographyService.decodeRSAPrivateKey(encoded);

        assertThat(decoded.getEncoded()).isEqualTo(original.getEncoded());
    }

    @Test
    void encodeAndDecodeECDSAPublicKey_roundTrip() {
        KeyPair keyPair = cryptographyService.generateECDSAKeyPair();
        PublicKey original = keyPair.getPublic();

        String encoded = cryptographyService.encodePublicKey(original);
        PublicKey decoded = cryptographyService.decodeECDSAPublicKey(encoded);

        assertThat(decoded.getEncoded()).isEqualTo(original.getEncoded());
    }

    // ==================== HMAC ====================

    @Test
    void generateHMAC_returnsNonNullString() {
        String hmac = cryptographyService.generateHMAC("test-data", "secret-key");

        assertThat(hmac).isNotNull().isNotEmpty();
    }

    @Test
    void generateHMAC_sameInputs_returnsSameHMAC() {
        String hmac1 = cryptographyService.generateHMAC("data", "secret");
        String hmac2 = cryptographyService.generateHMAC("data", "secret");

        assertThat(hmac1).isEqualTo(hmac2);
    }

    @Test
    void generateHMAC_differentData_returnsDifferentHMAC() {
        String hmac1 = cryptographyService.generateHMAC("data-1", "secret");
        String hmac2 = cryptographyService.generateHMAC("data-2", "secret");

        assertThat(hmac1).isNotEqualTo(hmac2);
    }

    @Test
    void generateHMAC_differentSecrets_returnsDifferentHMAC() {
        String hmac1 = cryptographyService.generateHMAC("data", "secret-1");
        String hmac2 = cryptographyService.generateHMAC("data", "secret-2");

        assertThat(hmac1).isNotEqualTo(hmac2);
    }

    @Test
    void verifyHMAC_validHMAC_returnsTrue() {
        String data = "batch-approval-data";
        String secret = "pharmatrust-secret";
        String hmac = cryptographyService.generateHMAC(data, secret);

        boolean valid = cryptographyService.verifyHMAC(data, hmac, secret);

        assertThat(valid).isTrue();
    }

    @Test
    void verifyHMAC_tamperedData_returnsFalse() {
        String secret = "pharmatrust-secret";
        String hmac = cryptographyService.generateHMAC("original-data", secret);

        boolean valid = cryptographyService.verifyHMAC("tampered-data", hmac, secret);

        assertThat(valid).isFalse();
    }

    @Test
    void verifyHMAC_wrongSecret_returnsFalse() {
        String data = "test-data";
        String hmac = cryptographyService.generateHMAC(data, "correct-secret");

        boolean valid = cryptographyService.verifyHMAC(data, hmac, "wrong-secret");

        assertThat(valid).isFalse();
    }

    // ==================== Merkle Root ====================

    @Test
    void calculateMerkleRoot_singleHash_returnsSameHash() {
        String hash = cryptographyService.generateSHA256Hash("unit-001");
        String root = cryptographyService.calculateMerkleRoot(List.of(hash));

        assertThat(root).isEqualTo(hash);
    }

    @Test
    void calculateMerkleRoot_twoHashes_returnsCombinedHash() {
        String h1 = cryptographyService.generateSHA256Hash("unit-001");
        String h2 = cryptographyService.generateSHA256Hash("unit-002");

        String root = cryptographyService.calculateMerkleRoot(List.of(h1, h2));

        assertThat(root).isNotNull().hasSize(64);
        assertThat(root).isNotEqualTo(h1).isNotEqualTo(h2);
    }

    @Test
    void calculateMerkleRoot_sameInputs_returnsSameRoot() {
        List<String> hashes = List.of(
                cryptographyService.generateSHA256Hash("unit-001"),
                cryptographyService.generateSHA256Hash("unit-002"),
                cryptographyService.generateSHA256Hash("unit-003")
        );

        String root1 = cryptographyService.calculateMerkleRoot(hashes);
        String root2 = cryptographyService.calculateMerkleRoot(hashes);

        assertThat(root1).isEqualTo(root2);
    }

    @Test
    void calculateMerkleRoot_differentInputs_returnsDifferentRoots() {
        List<String> hashes1 = List.of(
                cryptographyService.generateSHA256Hash("unit-001"),
                cryptographyService.generateSHA256Hash("unit-002")
        );
        List<String> hashes2 = List.of(
                cryptographyService.generateSHA256Hash("unit-003"),
                cryptographyService.generateSHA256Hash("unit-004")
        );

        String root1 = cryptographyService.calculateMerkleRoot(hashes1);
        String root2 = cryptographyService.calculateMerkleRoot(hashes2);

        assertThat(root1).isNotEqualTo(root2);
    }

    @Test
    void calculateMerkleRoot_emptyList_returnsEmptyString() {
        String root = cryptographyService.calculateMerkleRoot(List.of());

        assertThat(root).isEmpty();
    }

    @Test
    void calculateMerkleRoot_oddNumberOfHashes_handlesCorrectly() {
        List<String> hashes = List.of(
                cryptographyService.generateSHA256Hash("unit-001"),
                cryptographyService.generateSHA256Hash("unit-002"),
                cryptographyService.generateSHA256Hash("unit-003")
        );

        // Should not throw, odd count is handled by duplicating last element
        String root = cryptographyService.calculateMerkleRoot(hashes);

        assertThat(root).isNotNull().hasSize(64);
    }
}
