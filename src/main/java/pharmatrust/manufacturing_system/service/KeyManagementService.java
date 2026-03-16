package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Key management service with AWS Secrets Manager integration
 * Supports secure storage, retrieval, and rotation of manufacturer keys
 * Implements 90-day key rotation policy and 1-hour caching
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyManagementService {

    private final CryptographyService cryptographyService;
    private final SecretsManagerClient secretsManagerClient;

    @Value("${aws.secrets.enabled:false}")
    private boolean secretsEnabled;

    @Value("${aws.secrets.key-prefix:pharmatrust/}")
    private String keyPrefix;

    private static final int KEY_ROTATION_DAYS = 90;
    private static final String CACHE_NAME = "manufacturer-keys";

    /**
     * Store manufacturer key pair in AWS Secrets Manager
     * Keys are stored with rotation metadata
     */
    public void storeManufacturerKeys(String manufacturerId, KeyPair keyPair) {
        if (!secretsEnabled) {
            log.warn("AWS Secrets Manager is disabled. Keys not stored.");
            return;
        }

        try {
            String secretName = keyPrefix + "manufacturer/" + manufacturerId;
            
            // Encode keys to Base64
            String publicKey = cryptographyService.encodePublicKey(keyPair.getPublic());
            String privateKey = cryptographyService.encodePrivateKey(keyPair.getPrivate());
            
            // Create secret value with metadata
            Map<String, String> secretData = new HashMap<>();
            secretData.put("publicKey", publicKey);
            secretData.put("privateKey", privateKey);
            secretData.put("createdAt", Instant.now().toString());
            secretData.put("rotateAt", Instant.now().plus(KEY_ROTATION_DAYS, ChronoUnit.DAYS).toString());
            secretData.put("algorithm", "RSA");
            secretData.put("keySize", "2048");
            
            String secretValue = convertMapToJson(secretData);
            
            // Check if secret exists
            try {
                secretsManagerClient.describeSecret(DescribeSecretRequest.builder()
                    .secretId(secretName)
                    .build());
                
                // Update existing secret
                secretsManagerClient.updateSecret(UpdateSecretRequest.builder()
                    .secretId(secretName)
                    .secretString(secretValue)
                    .build());
                
                log.info("Updated manufacturer keys in Secrets Manager: {}", manufacturerId);
            } catch (ResourceNotFoundException e) {
                // Create new secret
                secretsManagerClient.createSecret(CreateSecretRequest.builder()
                    .name(secretName)
                    .secretString(secretValue)
                    .description("Manufacturer RSA key pair for " + manufacturerId)
                    .build());
                
                log.info("Stored new manufacturer keys in Secrets Manager: {}", manufacturerId);
            }
            
        } catch (Exception e) {
            log.error("Failed to store manufacturer keys in Secrets Manager", e);
            throw new RuntimeException("Failed to store keys", e);
        }
    }

    /**
     * Retrieve manufacturer private key from AWS Secrets Manager
     * Cached for 1 hour to reduce API calls
     */
    @Cacheable(value = CACHE_NAME, key = "'private-' + #manufacturerId")
    public PrivateKey getManufacturerPrivateKey(String manufacturerId) {
        if (!secretsEnabled) {
            log.warn("AWS Secrets Manager is disabled. Returning null.");
            return null;
        }

        try {
            String secretName = keyPrefix + "manufacturer/" + manufacturerId;
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build()
            );
            
            Map<String, String> secretData = parseJsonToMap(response.secretString());
            String privateKeyStr = secretData.get("privateKey");
            
            log.debug("Retrieved private key for manufacturer: {}", manufacturerId);
            return cryptographyService.decodeRSAPrivateKey(privateKeyStr);
            
        } catch (ResourceNotFoundException e) {
            log.error("Manufacturer keys not found in Secrets Manager: {}", manufacturerId);
            throw new RuntimeException("Manufacturer keys not found", e);
        } catch (Exception e) {
            log.error("Failed to retrieve manufacturer private key", e);
            throw new RuntimeException("Failed to retrieve private key", e);
        }
    }

    /**
     * Retrieve manufacturer public key from AWS Secrets Manager
     * Cached for 1 hour to reduce API calls
     */
    @Cacheable(value = CACHE_NAME, key = "'public-' + #manufacturerId")
    public PublicKey getManufacturerPublicKey(String manufacturerId) {
        if (!secretsEnabled) {
            log.warn("AWS Secrets Manager is disabled. Returning null.");
            return null;
        }

        try {
            String secretName = keyPrefix + "manufacturer/" + manufacturerId;
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build()
            );
            
            Map<String, String> secretData = parseJsonToMap(response.secretString());
            String publicKeyStr = secretData.get("publicKey");
            
            log.debug("Retrieved public key for manufacturer: {}", manufacturerId);
            return cryptographyService.decodeRSAPublicKey(publicKeyStr);
            
        } catch (ResourceNotFoundException e) {
            log.error("Manufacturer keys not found in Secrets Manager: {}", manufacturerId);
            throw new RuntimeException("Manufacturer keys not found", e);
        } catch (Exception e) {
            log.error("Failed to retrieve manufacturer public key", e);
            throw new RuntimeException("Failed to retrieve public key", e);
        }
    }

    /**
     * Check if manufacturer keys need rotation (90-day policy)
     */
    public boolean needsKeyRotation(String manufacturerId) {
        if (!secretsEnabled) {
            return false;
        }

        try {
            String secretName = keyPrefix + "manufacturer/" + manufacturerId;
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build()
            );
            
            Map<String, String> secretData = parseJsonToMap(response.secretString());
            String rotateAtStr = secretData.get("rotateAt");
            
            if (rotateAtStr == null) {
                return true; // No rotation date set, needs rotation
            }
            
            Instant rotateAt = Instant.parse(rotateAtStr);
            return Instant.now().isAfter(rotateAt);
            
        } catch (Exception e) {
            log.error("Failed to check key rotation status", e);
            return false;
        }
    }

    /**
     * Rotate manufacturer keys (generate new key pair)
     */
    public void rotateManufacturerKeys(String manufacturerId) {
        log.info("Rotating keys for manufacturer: {}", manufacturerId);
        KeyPair newKeyPair = cryptographyService.generateRSAKeyPair();
        storeManufacturerKeys(manufacturerId, newKeyPair);
        log.info("Successfully rotated keys for manufacturer: {}", manufacturerId);
    }

    /**
     * Delete manufacturer keys from Secrets Manager
     */
    public void deleteManufacturerKeys(String manufacturerId) {
        if (!secretsEnabled) {
            log.warn("AWS Secrets Manager is disabled. Keys not deleted.");
            return;
        }

        try {
            String secretName = keyPrefix + "manufacturer/" + manufacturerId;
            
            secretsManagerClient.deleteSecret(DeleteSecretRequest.builder()
                .secretId(secretName)
                .forceDeleteWithoutRecovery(false) // Allow 30-day recovery window
                .build());
            
            log.info("Deleted manufacturer keys from Secrets Manager: {}", manufacturerId);
            
        } catch (Exception e) {
            log.error("Failed to delete manufacturer keys", e);
            throw new RuntimeException("Failed to delete keys", e);
        }
    }

    /**
     * Simple JSON to Map converter (for secret data)
     */
    private Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.replace("{", "").replace("}", "").replace("\"", "");
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return map;
    }

    /**
     * Simple Map to JSON converter (for secret data)
     */
    private String convertMapToJson(Map<String, String> map) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (count > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"")
                .append(entry.getValue()).append("\"");
            count++;
        }
        json.append("}");
        return json.toString();
    }
}
