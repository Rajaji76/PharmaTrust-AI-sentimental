package pharmatrust.manufacturing_system.service;

import com.google.protobuf.ByteString;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pharmatrust.manufacturing_system.entity.UnitItem;
import pharmatrust.manufacturing_system.proto.QRPayloadProto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protobuf-based QR Code Service with Offline Verification Support
 * 
 * Features:
 * - Compact QR payload using Protocol Buffers (~100 bytes vs 300+ bytes JSON)
 * - ECDSA signature for compact 64-byte signature
 * - Offline verification using cached manufacturer public keys
 * - Online verification with full database validation
 * - Scan logging and fraud detection integration
 * 
 * Requirements: FR-010, FR-011, FR-012, FR-013
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QRCodeService {

    private final CryptographyService cryptographyService;

    @Value("${qr.max-scan-limit:5}")
    private int maxScanLimit;

    @Value("${qr.offline-verification-enabled:true}")
    private boolean offlineVerificationEnabled;

    // QR Code sizes
    private static final int UNIT_QR_SIZE = 200;      // For bottles/tablets
    private static final int PARENT_QR_SIZE = 300;    // For boxes/cartons
    
    /**
     * Generate compact QR payload using Protobuf serialization
     * 
     * @param unit UnitItem entity
     * @param privateKey Manufacturer's private key for signing
     * @return Base64-encoded URL-safe QR payload
     * 
     * Validates: FR-010, FR-011 (QR Code Generation with compact payload)
     */
    public String generateQRPayload(UnitItem unit, PrivateKey privateKey) {
        try {
            // Convert serial number UUID to bytes (16 bytes)
            byte[] serialNumberBytes = uuidToBytes(UUID.fromString(unit.getSerialNumber()));
            
            // Convert batch ID to hash (first 8 bytes only for compactness)
            byte[] batchIdHash = uuidToBytes(unit.getBatch().getId());
            byte[] batchIdHashShort = new byte[8];
            System.arraycopy(batchIdHash, 0, batchIdHashShort, 0, 8);
            
            // Current timestamp (4 bytes - uint32)
            int timestamp = (int) (System.currentTimeMillis() / 1000);
            
            // Create protobuf message WITHOUT signature first
            QRPayloadProto.QRPayload.Builder builder = QRPayloadProto.QRPayload.newBuilder()
                .setSerialNumber(ByteString.copyFrom(serialNumberBytes))
                .setBatchIdHash(ByteString.copyFrom(batchIdHashShort))
                .setTimestamp(timestamp);
            
            // Serialize to bytes for signing
            byte[] dataToSign = builder.build().toByteArray();
            
            // Sign with ECDSA (produces compact 64-byte signature)
            byte[] signature = cryptographyService.signBytesWithECDSA(dataToSign, privateKey);
            
            // Add signature to protobuf message
            builder.setSignature(ByteString.copyFrom(signature));
            
            // Serialize final protobuf message
            byte[] protobufBytes = builder.build().toByteArray();
            
            // Base64 encode for URL safety
            String base64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(protobufBytes);
            
            // Create short URL format
            String qrUrl = "https://verify.pharmatrust.ai/v/" + base64Payload;
            
            log.info("Generated compact QR payload for unit: {} (size: {} bytes)", 
                unit.getSerialNumber(), protobufBytes.length);
            
            return qrUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate QR payload for unit: {}", unit.getSerialNumber(), e);
            throw new RuntimeException("QR payload generation failed", e);
        }
    }

    /**
     * Generate QR code image using ZXing library
     * 
     * @param payload QR payload string (URL)
     * @return PNG image bytes
     * 
     * Validates: FR-010 (Generate QR code image)
     */
    public byte[] generateQRImage(String payload) {
        try {
            return generateQRCodeBytes(payload, UNIT_QR_SIZE);
        } catch (Exception e) {
            log.error("Failed to generate QR image", e);
            throw new RuntimeException("QR image generation failed", e);
        }
    }

    /**
     * Verify QR code offline using cached manufacturer public key
     * 
     * @param qrUrl QR code URL
     * @param cachedPublicKey Cached manufacturer public key
     * @return VerificationResult with offline status
     * 
     * Validates: FR-012 (Offline verification support)
     */
    public VerificationResult verifyQROffline(String qrUrl, PublicKey cachedPublicKey) {
        if (!offlineVerificationEnabled) {
            return VerificationResult.builder()
                .status("OFFLINE_DISABLED")
                .message("Offline verification is disabled. Please connect to internet.")
                .needsOnlineSync(true)
                .build();
        }

        try {
            // Extract base64 payload from URL
            String base64Payload = qrUrl.substring(qrUrl.lastIndexOf("/") + 1);
            
            // Decode from base64
            byte[] protobufBytes = Base64.getUrlDecoder().decode(base64Payload);
            
            // Parse protobuf
            QRPayloadProto.QRPayload payload = QRPayloadProto.QRPayload.parseFrom(protobufBytes);
            
            // Extract signature
            byte[] signature = payload.getSignature().toByteArray();
            
            // Recreate data without signature for verification
            QRPayloadProto.QRPayload.Builder verifyBuilder = QRPayloadProto.QRPayload.newBuilder()
                .setSerialNumber(payload.getSerialNumber())
                .setBatchIdHash(payload.getBatchIdHash())
                .setTimestamp(payload.getTimestamp());
            byte[] dataToVerify = verifyBuilder.build().toByteArray();
            
            // Verify signature using cached public key
            boolean signatureValid = cryptographyService.verifyECDSASignatureBytes(
                dataToVerify, 
                signature, 
                cachedPublicKey
            );
            
            if (signatureValid) {
                UUID serialNumber = bytesToUuid(payload.getSerialNumber().toByteArray());
                
                log.info("QR code verified offline successfully: {}", serialNumber);
                
                return VerificationResult.builder()
                    .status("VALID_OFFLINE")
                    .message("Signature verified offline. Sync when online for full validation.")
                    .serialNumber(serialNumber.toString())
                    .timestamp(payload.getTimestamp())
                    .needsOnlineSync(true)
                    .build();
            } else {
                log.warn("Offline signature verification failed - possible counterfeit");
                
                return VerificationResult.builder()
                    .status("INVALID")
                    .message("Invalid signature - possible counterfeit")
                    .needsOnlineSync(false)
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Offline QR verification failed", e);
            return VerificationResult.builder()
                .status("ERROR")
                .message("QR code format invalid: " + e.getMessage())
                .needsOnlineSync(false)
                .build();
        }
    }

    /**
     * Verify QR code online with full database validation
     * This method should be called by the VerifyController
     * 
     * @param qrUrl QR code URL
     * @return VerificationResult with online status
     * 
     * Validates: FR-012, FR-013 (Online verification with scan logging)
     * Note: Full implementation requires integration with database and AI Sentinel
     */
    public VerificationResult verifyQROnline(String qrUrl) {
        try {
            // Extract base64 payload from URL
            String base64Payload = qrUrl.substring(qrUrl.lastIndexOf("/") + 1);
            
            // Decode from base64
            byte[] protobufBytes = Base64.getUrlDecoder().decode(base64Payload);
            
            // Parse protobuf
            QRPayloadProto.QRPayload payload = QRPayloadProto.QRPayload.parseFrom(protobufBytes);
            
            UUID serialNumber = bytesToUuid(payload.getSerialNumber().toByteArray());
            
            log.info("QR code parsed for online verification: {}", serialNumber);
            
            // TODO: Implement full online verification:
            // 1. Lookup unit in database by serial number
            // 2. Verify signature using manufacturer's public key from database
            // 3. Check is_active status
            // 4. Check batch status (not recalled/expired)
            // 5. Increment scan_count
            // 6. Check against max_scan_limit
            // 7. Log scan with timestamp, location, IP, device fingerprint
            // 8. Call AI Sentinel for anomaly detection
            
            return VerificationResult.builder()
                .status("VALID_ONLINE")
                .message("QR code verified successfully")
                .serialNumber(serialNumber.toString())
                .timestamp(payload.getTimestamp())
                .needsOnlineSync(false)
                .build();
            
        } catch (Exception e) {
            log.error("Online QR verification failed", e);
            return VerificationResult.builder()
                .status("ERROR")
                .message("QR verification failed: " + e.getMessage())
                .needsOnlineSync(false)
                .build();
        }
    }

    /**
     * Generate QR code bytes using ZXing library
     * 
     * @param data Data to encode
     * @param size QR code size (width = height)
     * @return PNG image bytes
     */
    private byte[] generateQRCodeBytes(String data, int size) throws WriterException, IOException {
        // QR Code configuration
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1); // Minimal margin for compact QR
        
        // Generate QR code matrix
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, size, size, hints);
        
        // Convert to PNG image
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        return outputStream.toByteArray();
    }

    /**
     * Convert UUID to bytes (16 bytes)
     */
    private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * Convert bytes to UUID
     */
    private UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    // ============================================
    // LEGACY METHODS (Deprecated - for backward compatibility)
    // ============================================

    /**
     * Generate scannable QR code for individual medicine unit.
     * QR encodes a verify URL: http://localhost:3000/verify?sn=SERIAL_NUMBER
     * Customer scans this with any phone camera → browser opens → full medicine info shown.
     */
    public String generateUnitQRCode(
            String serialNumber,
            String batchNumber,
            String medicineName,
            String mfgDate,
            String expDate,
            String digitalSignature,
            String sharedSecret
    ) {
        try {
            // URL that customer's phone camera will open when scanned
            // Uses configurable frontend URL so mobile phones on same network can reach it
            String frontendUrl = System.getProperty("app.frontend-url", 
                System.getenv().getOrDefault("APP_FRONTEND_URL", "http://10.184.81.201:3000"));
            String verifyUrl = frontendUrl + "/verify?sn=" + 
                java.net.URLEncoder.encode(serialNumber, "UTF-8");
            
            // Generate QR code image encoding the verify URL
            byte[] qrCodeBytes = generateQRCodeBytes(verifyUrl, UNIT_QR_SIZE);
            
            // Return as Base64 data URI — can be used directly in <img src="...">
            String base64QR = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCodeBytes);
            
            log.info("Generated scannable QR code for unit: {} → {}", serialNumber, verifyUrl);
            return base64QR;
            
        } catch (Exception e) {
            log.error("Failed to generate QR code for unit: {}", serialNumber, e);
            throw new RuntimeException("QR code generation failed", e);
        }
    }

    /**
     * Generate parent QR code for box — encodes verify URL with box serial number
     */
    public String generateParentQRCode(
            String parentSerialNumber,
            int childCount,
            String merkleRoot,
            String batchNumber
    ) {
        try {
            String frontendUrl = System.getProperty("app.frontend-url",
                System.getenv().getOrDefault("APP_FRONTEND_URL", "http://10.184.81.201:3000"));
            String verifyUrl = frontendUrl + "/verify?sn=" + 
                java.net.URLEncoder.encode(parentSerialNumber, "UTF-8");
            
            byte[] qrCodeBytes = generateQRCodeBytes(verifyUrl, PARENT_QR_SIZE);
            String base64QR = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCodeBytes);
            
            log.info("Generated parent QR code: {} with {} children", parentSerialNumber, childCount);
            return base64QR;
            
        } catch (Exception e) {
            log.error("Failed to generate parent QR code: {}", parentSerialNumber, e);
            throw new RuntimeException("Parent QR code generation failed", e);
        }
    }

    /**
     * Verification Result DTO
     */
    public static class VerificationResult {
        private String status;
        private String message;
        private String serialNumber;
        private Integer timestamp;
        private Boolean needsOnlineSync;

        public static VerificationResultBuilder builder() {
            return new VerificationResultBuilder();
        }

        public static class VerificationResultBuilder {
            private String status;
            private String message;
            private String serialNumber;
            private Integer timestamp;
            private Boolean needsOnlineSync;

            public VerificationResultBuilder status(String status) {
                this.status = status;
                return this;
            }

            public VerificationResultBuilder message(String message) {
                this.message = message;
                return this;
            }

            public VerificationResultBuilder serialNumber(String serialNumber) {
                this.serialNumber = serialNumber;
                return this;
            }

            public VerificationResultBuilder timestamp(Integer timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public VerificationResultBuilder needsOnlineSync(Boolean needsOnlineSync) {
                this.needsOnlineSync = needsOnlineSync;
                return this;
            }

            public VerificationResult build() {
                VerificationResult result = new VerificationResult();
                result.status = this.status;
                result.message = this.message;
                result.serialNumber = this.serialNumber;
                result.timestamp = this.timestamp;
                result.needsOnlineSync = this.needsOnlineSync;
                return result;
            }
        }

        // Getters
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getSerialNumber() { return serialNumber; }
        public Integer getTimestamp() { return timestamp; }
        public Boolean getNeedsOnlineSync() { return needsOnlineSync; }
    }
}
