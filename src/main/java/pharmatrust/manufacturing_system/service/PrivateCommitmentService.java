package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

/**
 * Private Commitment Service - Encrypted Blockchain Data Privacy
 * 
 * Problem: If all data goes on public blockchain, competitors can see your business metrics
 * Solution: Store only encrypted "commitments" on blockchain (like sealed envelopes)
 * 
 * Approach: Hyperledger Fabric style Private Side-Channels
 * - Public blockchain stores: Encrypted Hash (Commitment)
 * - Private database stores: Actual data
 * - Only authorized parties with decryption key can read data
 * 
 * Encryption: AES-256-GCM (Authenticated Encryption)
 * Key Management: Asymmetric RSA for key exchange
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PrivateCommitmentService {

    private final CryptographyService cryptographyService;
    
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256; // 256-bit AES
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    private static final int GCM_IV_LENGTH = 12; // 12-byte IV for GCM

    /**
     * Create encrypted commitment for blockchain
     * 
     * Use Case: Manufacturer creates batch with 100,000 units
     * - Actual data (serial numbers, quantities) stays in private database
     * - Only encrypted commitment goes to blockchain
     * - Competitors see commitment but can't decrypt without key
     * 
     * @param data Sensitive data to commit (e.g., batch details JSON)
     * @param recipientPublicKey Public key of authorized party (e.g., regulator)
     * @return Encrypted commitment (Base64 encoded)
     */
    public EncryptedCommitment createCommitment(String data, PublicKey recipientPublicKey) {
        try {
            // Step 1: Generate random AES key for this commitment
            SecretKey aesKey = generateAESKey();
            
            // Step 2: Encrypt data with AES-GCM
            byte[] encryptedData = encryptWithAES(data.getBytes(), aesKey);
            
            // Step 3: Encrypt AES key with recipient's RSA public key
            byte[] encryptedAESKey = encryptAESKeyWithRSA(aesKey, recipientPublicKey);
            
            // Step 4: Create commitment hash (for blockchain verification)
            String commitmentHash = cryptographyService.hashSHA256(data);
            
            log.info("Created encrypted commitment with hash: {}", commitmentHash);
            
            return EncryptedCommitment.builder()
                    .encryptedData(Base64.getEncoder().encodeToString(encryptedData))
                    .encryptedKey(Base64.getEncoder().encodeToString(encryptedAESKey))
                    .commitmentHash(commitmentHash)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to create encrypted commitment", e);
            throw new RuntimeException("Commitment creation failed", e);
        }
    }

    /**
     * Verify and decrypt commitment
     * 
     * Use Case: Regulator wants to audit batch data
     * - Regulator provides their private key
     * - System decrypts AES key using RSA private key
     * - System decrypts data using AES key
     * - System verifies commitment hash matches
     * 
     * @param commitment Encrypted commitment from blockchain
     * @param recipientPrivateKey Private key of authorized party
     * @return Decrypted data (if authorized)
     */
    public String decryptCommitment(EncryptedCommitment commitment, PrivateKey recipientPrivateKey) {
        try {
            // Step 1: Decrypt AES key using RSA private key
            byte[] encryptedAESKey = Base64.getDecoder().decode(commitment.getEncryptedKey());
            SecretKey aesKey = decryptAESKeyWithRSA(encryptedAESKey, recipientPrivateKey);
            
            // Step 2: Decrypt data using AES key
            byte[] encryptedData = Base64.getDecoder().decode(commitment.getEncryptedData());
            byte[] decryptedData = decryptWithAES(encryptedData, aesKey);
            
            String data = new String(decryptedData);
            
            // Step 3: Verify commitment hash
            String calculatedHash = cryptographyService.hashSHA256(data);
            if (!calculatedHash.equals(commitment.getCommitmentHash())) {
                throw new SecurityException("Commitment hash mismatch! Data may be tampered.");
            }
            
            log.info("Successfully decrypted and verified commitment");
            return data;
            
        } catch (Exception e) {
            log.error("Failed to decrypt commitment", e);
            throw new RuntimeException("Commitment decryption failed", e);
        }
    }

    /**
     * Create public commitment (for blockchain storage)
     * Only stores hash and encrypted key - no actual data
     * 
     * @param commitment Full encrypted commitment
     * @return Public commitment (safe for blockchain)
     */
    public PublicCommitment createPublicCommitment(EncryptedCommitment commitment) {
        return PublicCommitment.builder()
                .commitmentHash(commitment.getCommitmentHash())
                .encryptedKey(commitment.getEncryptedKey())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Verify commitment integrity without decryption
     * Used by blockchain validators
     * 
     * @param publicCommitment Public commitment from blockchain
     * @param expectedHash Expected hash value
     * @return true if commitment is valid
     */
    public boolean verifyCommitmentIntegrity(PublicCommitment publicCommitment, String expectedHash) {
        return publicCommitment.getCommitmentHash().equals(expectedHash);
    }

    // ==================== AES Encryption Methods ====================

    /**
     * Generate random AES-256 key
     */
    private SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Encrypt data with AES-GCM
     * GCM provides both confidentiality and authenticity
     */
    private byte[] encryptWithAES(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] encryptedData = cipher.doFinal(data);
        
        // Prepend IV to encrypted data
        byte[] result = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);
        
        return result;
    }

    /**
     * Decrypt data with AES-GCM
     */
    private byte[] decryptWithAES(byte[] encryptedDataWithIV, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        
        // Extract IV from beginning
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encryptedDataWithIV, 0, iv, 0, GCM_IV_LENGTH);
        
        // Extract encrypted data
        byte[] encryptedData = new byte[encryptedDataWithIV.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedDataWithIV, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
        
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        
        return cipher.doFinal(encryptedData);
    }

    // ==================== RSA Key Encryption Methods ====================

    /**
     * Encrypt AES key with RSA public key
     * Allows secure key exchange
     */
    private byte[] encryptAESKeyWithRSA(SecretKey aesKey, PublicKey rsaPublicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        return cipher.doFinal(aesKey.getEncoded());
    }

    /**
     * Decrypt AES key with RSA private key
     */
    private SecretKey decryptAESKeyWithRSA(byte[] encryptedKey, PrivateKey rsaPrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] decryptedKey = cipher.doFinal(encryptedKey);
        return new SecretKeySpec(decryptedKey, AES_ALGORITHM);
    }

    // ==================== DTOs ====================

    /**
     * Full encrypted commitment (stored in private database)
     */
    @lombok.Data
    @lombok.Builder
    public static class EncryptedCommitment {
        private String encryptedData;      // AES-encrypted actual data
        private String encryptedKey;       // RSA-encrypted AES key
        private String commitmentHash;     // SHA-256 hash for verification
    }

    /**
     * Public commitment (stored on blockchain)
     * Does NOT contain actual data - only hash and encrypted key
     */
    @lombok.Data
    @lombok.Builder
    public static class PublicCommitment {
        private String commitmentHash;     // SHA-256 hash (public)
        private String encryptedKey;       // RSA-encrypted AES key (only recipient can decrypt)
        private long timestamp;            // Commitment creation time
    }
}
