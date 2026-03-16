package pharmatrust.manufacturing_system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Cryptography service for digital signatures, hashing, and HMAC
 * Supports RSA/ECDSA signatures for batch and unit authentication
 * Implements SHA-256 hashing for file integrity verification
 */
@Service
@Slf4j
public class CryptographyService {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String ECDSA_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM_RSA = "SHA256withRSA";
    private static final String SIGNATURE_ALGORITHM_ECDSA = "SHA256withECDSA";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int RSA_KEY_SIZE = 2048;
    private static final String EC_CURVE = "secp256r1";

    /**
     * Generate SHA-256 hash of data
     * Used for file integrity verification (lab reports)
     */
    public String generateSHA256Hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate hash", e);
        }
    }

    /**
     * Generate SHA-256 hash of string data
     */
    public String generateSHA256Hash(String data) {
        return generateSHA256Hash(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Generate SHA-256 hash of string data (alias for compatibility)
     */
    public String hashSHA256(String data) {
        return generateSHA256Hash(data);
    }

    /**
     * Generate RSA key pair for manufacturer
     * 2048-bit keys for strong security
     */
    public KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyGen.initialize(RSA_KEY_SIZE, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            log.info("Generated RSA key pair with {} bit key size", RSA_KEY_SIZE);
            return keyPair;
        } catch (NoSuchAlgorithmException e) {
            log.error("RSA algorithm not available", e);
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * Generate ECDSA key pair for compact signatures
     * Used for QR code signatures (64 bytes vs 256 bytes for RSA)
     */
    public KeyPair generateECDSAKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ECDSA_ALGORITHM);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE);
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            log.info("Generated ECDSA key pair with curve {}", EC_CURVE);
            return keyPair;
        } catch (Exception e) {
            log.error("Failed to generate ECDSA key pair", e);
            throw new RuntimeException("Failed to generate ECDSA key pair", e);
        }
    }

    /**
     * Sign data with RSA private key
     * Used for batch digital signatures
     */
    public String signDataWithRSA(String data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_RSA);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign data with RSA", e);
            throw new RuntimeException("Failed to sign data", e);
        }
    }

    /**
     * Sign data with ECDSA private key
     * Used for compact QR code signatures
     */
    public String signDataWithECDSA(String data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_ECDSA);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign data with ECDSA", e);
            throw new RuntimeException("Failed to sign data", e);
        }
    }

    /**
     * Sign byte array with ECDSA (for Protobuf QR payloads)
     */
    public byte[] signBytesWithECDSA(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_ECDSA);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            log.error("Failed to sign bytes with ECDSA", e);
            throw new RuntimeException("Failed to sign bytes", e);
        }
    }

    /**
     * Verify RSA signature
     */
    public boolean verifyRSASignature(String data, String signatureStr, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_RSA);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to verify RSA signature", e);
            return false;
        }
    }

    /**
     * Verify ECDSA signature
     */
    public boolean verifyECDSASignature(String data, String signatureStr, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_ECDSA);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to verify ECDSA signature", e);
            return false;
        }
    }

    /**
     * Verify ECDSA signature for byte arrays (for Protobuf QR payloads)
     */
    public boolean verifyECDSASignatureBytes(byte[] data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_ECDSA);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to verify ECDSA signature bytes", e);
            return false;
        }
    }

    /**
     * Generate HMAC for QR payload integrity
     * Prevents tampering with QR code data
     */
    public String generateHMAC(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), 
                HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to generate HMAC", e);
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }

    /**
     * Verify HMAC token
     */
    public boolean verifyHMAC(String data, String hmac, String secret) {
        try {
            String calculatedHmac = generateHMAC(data, secret);
            return MessageDigest.isEqual(
                calculatedHmac.getBytes(StandardCharsets.UTF_8),
                hmac.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify HMAC", e);
            return false;
        }
    }

    /**
     * Encode public key to Base64 string for storage
     */
    public String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Encode private key to Base64 string for storage
     */
    public String encodePrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Decode RSA public key from Base64 string
     */
    public PublicKey decodeRSAPublicKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to decode RSA public key", e);
            throw new RuntimeException("Failed to decode public key", e);
        }
    }

    /**
     * Decode RSA private key from Base64 string
     */
    public PrivateKey decodeRSAPrivateKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            log.error("Failed to decode RSA private key", e);
            throw new RuntimeException("Failed to decode private key", e);
        }
    }

    /**
     * Decode ECDSA public key from Base64 string
     */
    public PublicKey decodeECDSAPublicKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ECDSA_ALGORITHM);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to decode ECDSA public key", e);
            throw new RuntimeException("Failed to decode public key", e);
        }
    }

    /**
     * Decode ECDSA private key from Base64 string
     */
    public PrivateKey decodeECDSAPrivateKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ECDSA_ALGORITHM);
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            log.error("Failed to decode ECDSA private key", e);
            throw new RuntimeException("Failed to decode private key", e);
        }
    }

    /**
     * Convert byte array to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Calculate Merkle root from list of hashes
     * Used for blockchain batch verification
     */
    public String calculateMerkleRoot(java.util.List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return "";
        }
        
        if (hashes.size() == 1) {
            return hashes.get(0);
        }
        
        java.util.List<String> newLevel = new java.util.ArrayList<>();
        
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
            String combined = left + right;
            String hash = hashSHA256(combined);
            newLevel.add(hash);
        }
        
        return calculateMerkleRoot(newLevel);
    }
}
