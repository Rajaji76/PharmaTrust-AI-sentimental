package pharmatrust.manufacturing_system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * TOTP (Time-based One-Time Password) Service for Offline QR Verification
 * 
 * Problem: Warehouses in remote areas don't have internet. How to verify QR codes offline?
 * Solution: Embed time-based OTP in QR code that can be verified without server connection
 * 
 * Algorithm: RFC 6238 TOTP (Google Authenticator style)
 * - Uses HMAC-SHA256 with shared secret
 * - Time window: 30 seconds (configurable)
 * - Allows clock skew tolerance: ±1 window (90 seconds total)
 */
@Service
@Slf4j
public class TOTPService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TIME_STEP_SECONDS = 30; // 30-second time window
    private static final int TOTP_DIGITS = 8; // 8-digit OTP
    private static final int CLOCK_SKEW_WINDOWS = 1; // Allow ±1 window (±30 seconds)

    /**
     * Generate TOTP for current time
     * Used when creating QR codes
     * 
     * @param secret Shared secret key (stored in AWS Secrets Manager)
     * @param serialNumber Unit serial number (for uniqueness)
     * @return 8-digit TOTP code
     */
    public String generateTOTP(String secret, String serialNumber) {
        long currentTime = Instant.now().getEpochSecond();
        return generateTOTP(secret, serialNumber, currentTime);
    }

    /**
     * Generate TOTP for specific timestamp
     * 
     * @param secret Shared secret key
     * @param serialNumber Unit serial number
     * @param timestamp Unix timestamp in seconds
     * @return 8-digit TOTP code
     */
    public String generateTOTP(String secret, String serialNumber, long timestamp) {
        try {
            // Calculate time counter (30-second windows)
            long timeCounter = timestamp / TIME_STEP_SECONDS;
            
            // Combine secret with serial number for unit-specific TOTP
            String combinedSecret = secret + ":" + serialNumber;
            
            // Generate HMAC
            byte[] hash = generateHMAC(combinedSecret, timeCounter);
            
            // Dynamic truncation (RFC 6238)
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            
            // Generate TOTP_DIGITS code
            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            
            // Pad with leading zeros
            return String.format("%0" + TOTP_DIGITS + "d", otp);
            
        } catch (Exception e) {
            log.error("Failed to generate TOTP for serial: {}", serialNumber, e);
            throw new RuntimeException("TOTP generation failed", e);
        }
    }

    /**
     * Verify TOTP with clock skew tolerance
     * Allows ±30 seconds (±1 window) for clock differences
     * 
     * @param totp TOTP code from QR scan
     * @param secret Shared secret key
     * @param serialNumber Unit serial number
     * @return true if TOTP is valid within time window
     */
    public boolean verifyTOTP(String totp, String secret, String serialNumber) {
        long currentTime = Instant.now().getEpochSecond();
        
        // Check current window and adjacent windows (±CLOCK_SKEW_WINDOWS)
        for (int i = -CLOCK_SKEW_WINDOWS; i <= CLOCK_SKEW_WINDOWS; i++) {
            long adjustedTime = currentTime + (i * TIME_STEP_SECONDS);
            String expectedTOTP = generateTOTP(secret, serialNumber, adjustedTime);
            
            if (constantTimeEquals(totp, expectedTOTP)) {
                if (i != 0) {
                    log.warn("TOTP verified with clock skew: {} windows for serial: {}", i, serialNumber);
                }
                return true;
            }
        }
        
        log.warn("TOTP verification failed for serial: {}", serialNumber);
        return false;
    }

    /**
     * Generate HMAC-SHA256 hash
     */
    private byte[] generateHMAC(String secret, long counter) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), HMAC_ALGORITHM);
        mac.init(keySpec);
        
        // Convert counter to 8-byte array (big-endian)
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(counter);
        
        return mac.doFinal(buffer.array());
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }

    /**
     * Get remaining validity time for current TOTP window
     * Useful for UI countdown timers
     * 
     * @return Seconds remaining in current time window
     */
    public int getRemainingValiditySeconds() {
        long currentTime = Instant.now().getEpochSecond();
        return (int) (TIME_STEP_SECONDS - (currentTime % TIME_STEP_SECONDS));
    }

    /**
     * Check if TOTP is about to expire (< 5 seconds remaining)
     * Used to warn users to refresh QR code
     */
    public boolean isTOTPExpiringSoon() {
        return getRemainingValiditySeconds() < 5;
    }
}
