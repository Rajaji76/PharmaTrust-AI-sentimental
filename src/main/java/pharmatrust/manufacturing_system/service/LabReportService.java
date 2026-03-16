package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Lab Report Service
 * 
 * Features:
 * - Upload lab reports to AWS S3
 * - Calculate SHA-256 hash for integrity
 * - Verify test officer digital signature
 * - Store hash on blockchain
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LabReportService {

    private final S3Client s3Client;
    private final CryptographyService cryptographyService;

    @Value("${aws.s3.bucket-name:pharmatrust-lab-reports}")
    private String bucketName;

    @Value("${aws.s3.mock-upload:true}")
    private boolean mockUpload;

    /**
     * Upload lab report to S3 and calculate hash.
     * If mock-upload=true (dev mode), skips actual S3 upload and returns local hash.
     */
    public LabReportUploadResult uploadLabReport(
            MultipartFile file,
            String batchNumber,
            String testOfficerSignature
    ) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Lab report file is empty");
            }

            // Calculate SHA-256 hash of file content
            byte[] fileBytes = file.getBytes();
            String fileHash = cryptographyService.hashSHA256(new String(fileBytes));

            // Extract text from PDF for AI verification
            String extractedText = extractPdfText(fileBytes);
            log.info("[LabReport] Extracted {} chars from PDF for AI verification", extractedText.length());

            // Generate S3 key
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String s3Key = String.format("lab-reports/%s/%s_%s.pdf",
                batchNumber, timestamp, UUID.randomUUID().toString().substring(0, 8));

            if (mockUpload) {
                // Dev mode: skip actual S3 upload, just return hash
                log.info("[MOCK] Lab report hash calculated: {} (S3 upload skipped in dev mode)", fileHash);
            } else {
                // Production: upload to real S3
                String contentType = file.getContentType();
                if (contentType == null || !contentType.equals("application/pdf")) {
                    throw new RuntimeException("Only PDF files are allowed for lab reports");
                }
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("application/pdf")
                    .metadata(java.util.Map.of(
                        "batch-number", batchNumber,
                        "file-hash", fileHash,
                        "test-officer-signature", testOfficerSignature,
                        "upload-timestamp", timestamp
                    ))
                    .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
                log.info("Lab report uploaded to S3: {} with hash: {}", s3Key, fileHash);
            }

            return LabReportUploadResult.builder()
                .s3Key(s3Key)
                .fileHash(fileHash)
                .extractedText(extractedText)
                .testOfficerSignature(testOfficerSignature)
                .uploadTimestamp(LocalDateTime.now())
                .success(true)
                .build();

        } catch (IOException e) {
            log.error("Failed to process lab report for batch: {}", batchNumber, e);
            throw new RuntimeException("Lab report processing failed", e);
        }
    }

    /**
     * Extract plain text from PDF bytes using PDFBox.
     * Tries multiple strategies to handle Word-generated and scanned PDFs.
     * Returns empty string if extraction fails.
     */
    private String extractPdfText(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Sort by position for better word ordering in Word-generated PDFs
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            if (text != null && text.trim().length() > 20) {
                log.info("[LabReport] Extracted {} chars via PDFTextStripper", text.length());
                return text.toLowerCase();
            }
            // Fallback: try without sort
            PDFTextStripper fallback = new PDFTextStripper();
            fallback.setSortByPosition(false);
            String text2 = fallback.getText(doc);
            log.info("[LabReport] Fallback extraction: {} chars", text2 != null ? text2.length() : 0);
            return text2 != null ? text2.toLowerCase() : "";
        } catch (Exception e) {
            log.warn("[LabReport] PDF text extraction failed — file may not be a valid PDF: {}", e.getMessage());
            return ""; // empty → AI will fail verification
        }
    }

    /**
     * Verify test officer signature
     * 
     * @param signature Digital signature
     * @param publicKey Test officer's public key (Base64 encoded)
     * @param data Data that was signed
     * @return true if signature is valid
     */
    public boolean verifyTestOfficerSignature(String signature, String publicKey, String data) {
        try {
            // Decode public key and verify RSA signature
            PublicKey pubKey = cryptographyService.decodeRSAPublicKey(publicKey);
            return cryptographyService.verifyRSASignature(data, signature, pubKey);
        } catch (Exception e) {
            log.error("Failed to verify test officer signature", e);
            return false;
        }
    }

    /**
     * Lab Report Upload Result DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class LabReportUploadResult {
        private String s3Key;
        private String fileHash;
        private String extractedText;   // actual text extracted from PDF
        private String testOfficerSignature;
        private LocalDateTime uploadTimestamp;
        private boolean success;
    }
}
