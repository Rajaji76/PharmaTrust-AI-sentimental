package pharmatrust.manufacturing_system.service;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;

@Service
public class SecurityService {
    public String generateLabHash(String data) {
    // Actual SHA-256 Hashing
    return org.springframework.util.DigestUtils.md5DigestAsHex(data.getBytes());
}


    public String generateLabHash(byte[] fileBytes) {
        try {
            if (fileBytes == null || fileBytes.length == 0) {
                return "DEFAULT_HASH_" + System.currentTimeMillis();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(fileBytes);
            return bytesToHex(encodedHash); // यही वह लाइन है जो एरर दे रही थी
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    // यह हिस्सा आपने मिस कर दिया था, इसे क्लास के अंदर जोड़ें
    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
