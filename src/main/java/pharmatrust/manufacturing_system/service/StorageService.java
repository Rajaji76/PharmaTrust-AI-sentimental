package pharmatrust.manufacturing_system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Storage service for AWS S3 lab report management
 * Implements file upload with encryption, integrity verification, and secure access
 * 
 * Requirements: FR-021, FR-022, FR-023, BR-002, NFR-007, NFR-011
 */
@Service
@Slf4j
public class StorageService {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private CryptographyService cryptographyService;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.presigned-url-expiration-minutes:15}")
    private int presignedUrlExpirationMinutes;

    /**
     * Upload lab report to S3 with encryption and hash calculation
     * 
     * @param file MultipartFile containing the lab report PDF
     * @param batchId UUID of the batch this report belongs to
     * @return S3 key of the uploaded file
     * @throws IOException if file reading fails
     * 
     * Validates: FR-021 (Lab Report Upload)
     * - Accepts PDF files up to 10MB
     * - Calculates SHA-256 hash before upload
     * - Uploads to AWS S3 with encryption (SSE-S3)
     * - Returns S3 key for database storage
     */
    public String uploadLabReport(MultipartFile file, UUID batchId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        // Validate file size (10MB max)
        long maxSize = 10 * 1024 * 1024; // 10MB in bytes
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        // Validate file type (PDF only)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            log.warn("Invalid content type: {}. Expected application/pdf", contentType);
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        // Read file bytes
        byte[] fileBytes = file.getBytes();

        // Calculate SHA-256 hash before upload (FR-021)
        String fileHash = cryptographyService.generateSHA256Hash(fileBytes);
        log.info("Calculated SHA-256 hash for lab report: {}", fileHash);

        // Generate unique S3 key: lab-reports/{batchId}/{uuid}.pdf
        String s3Key = String.format("lab-reports/%s/%s.pdf", batchId, UUID.randomUUID());

        try {
            // Upload to S3 with server-side encryption (SSE-S3) - NFR-007
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("application/pdf")
                .serverSideEncryption(ServerSideEncryption.AES256) // SSE-S3 encryption
                .metadata(java.util.Map.of(
                    "batch-id", batchId.toString(),
                    "file-hash", fileHash,
                    "upload-timestamp", java.time.Instant.now().toString()
                ))
                .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes));

            log.info("Successfully uploaded lab report to S3: bucket={}, key={}", bucketName, s3Key);
            return s3Key;

        } catch (S3Exception e) {
            log.error("Failed to upload lab report to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload lab report to S3", e);
        }
    }

    /**
     * Generate pre-signed URL for secure lab report access
     * 
     * @param s3Key S3 key of the lab report
     * @param expirationMinutes URL expiration time in minutes
     * @return Pre-signed URL string
     * 
     * Validates: FR-023 (Lab Report Access)
     * - Generates pre-signed URLs for secure access
     * - URLs expire after specified minutes (default: 15)
     * - Only authorized users can access lab reports
     */
    public String generatePresignedUrl(String s3Key, int expirationMinutes) {
        if (s3Key == null || s3Key.isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }

        try {
            // Create S3 Presigner
            S3Presigner presigner = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();

            // Create GetObject request
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            // Generate pre-signed URL with expiration (FR-023)
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.info("Generated pre-signed URL for S3 key: {} (expires in {} minutes)", s3Key, expirationMinutes);
            
            presigner.close();
            return url;

        } catch (S3Exception e) {
            log.error("Failed to generate pre-signed URL for S3 key {}: {}", s3Key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate pre-signed URL", e);
        }
    }

    /**
     * Generate pre-signed URL with default expiration (15 minutes)
     * 
     * @param s3Key S3 key of the lab report
     * @return Pre-signed URL string
     */
    public String generatePresignedUrl(String s3Key) {
        return generatePresignedUrl(s3Key, presignedUrlExpirationMinutes);
    }

    /**
     * Verify file integrity by comparing stored hash with current file hash
     * 
     * @param s3Key S3 key of the lab report
     * @param expectedHash Expected SHA-256 hash from database
     * @return true if hashes match, false otherwise
     * 
     * Validates: FR-022 (Lab Report Integrity)
     * - Verifies file integrity on retrieval
     * - Compares current hash with stored hash
     * - Detects tampering if hash mismatch
     */
    public boolean verifyFileIntegrity(String s3Key, String expectedHash) {
        if (s3Key == null || s3Key.isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }

        if (expectedHash == null || expectedHash.isEmpty()) {
            throw new IllegalArgumentException("Expected hash cannot be null or empty");
        }

        try {
            // Download file from S3
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            byte[] fileBytes = s3Client.getObjectAsBytes(getRequest).asByteArray();

            // Calculate current hash
            String currentHash = cryptographyService.generateSHA256Hash(fileBytes);

            // Compare hashes (FR-022)
            boolean isValid = currentHash.equals(expectedHash);

            if (isValid) {
                log.info("File integrity verified for S3 key: {}", s3Key);
            } else {
                log.error("FILE INTEGRITY VIOLATION DETECTED! S3 key: {}, Expected: {}, Current: {}", 
                    s3Key, expectedHash, currentHash);
                // Generate alert for regulators (FR-022)
                generateTamperingAlert(s3Key, expectedHash, currentHash);
            }

            return isValid;

        } catch (S3Exception e) {
            log.error("Failed to verify file integrity for S3 key {}: {}", s3Key, e.getMessage(), e);
            throw new RuntimeException("Failed to verify file integrity", e);
        }
    }

    /**
     * Delete file from S3 (cleanup operations)
     * 
     * @param s3Key S3 key of the file to delete
     * 
     * Validates: FR-022 (deleteFile for cleanup operations)
     */
    public void deleteFile(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            s3Client.deleteObject(deleteRequest);

            log.info("Successfully deleted file from S3: bucket={}, key={}", bucketName, s3Key);

        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    /**
     * Generate tampering alert for regulators
     * 
     * @param s3Key S3 key of the tampered file
     * @param expectedHash Expected hash
     * @param currentHash Current hash
     * 
     * Validates: FR-022 (Generate alert on tampering)
     */
    private void generateTamperingAlert(String s3Key, String expectedHash, String currentHash) {
        // TODO: Integrate with alert service when implemented
        log.error("TAMPERING ALERT: Lab report integrity violation detected");
        log.error("S3 Key: {}", s3Key);
        log.error("Expected Hash: {}", expectedHash);
        log.error("Current Hash: {}", currentHash);
        log.error("Action Required: Notify regulators and investigate batch");
        
        // This would typically send alerts via:
        // - Email to regulators
        // - SMS notifications
        // - Dashboard alerts
        // - Audit log entries
    }

    /**
     * Check if file exists in S3
     * 
     * @param s3Key S3 key to check
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return false;
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            s3Client.headObject(headRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Error checking file existence for S3 key {}: {}", s3Key, e.getMessage());
            return false;
        }
    }

    /**
     * Get file metadata from S3
     * 
     * @param s3Key S3 key of the file
     * @return HeadObjectResponse containing metadata
     */
    public HeadObjectResponse getFileMetadata(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            return s3Client.headObject(headRequest);

        } catch (S3Exception e) {
            log.error("Failed to get file metadata for S3 key {}: {}", s3Key, e.getMessage(), e);
            throw new RuntimeException("Failed to get file metadata", e);
        }
    }
}
