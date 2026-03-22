package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StorageService (S3 operations mocked)
 * Tests: file upload, integrity verification, presigned URL, delete
 * Requirements: FR-021, FR-022, FR-023, NFR-007, NFR-011
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private CryptographyService cryptographyService;

    @InjectMocks
    private StorageService storageService;

    private UUID batchId;
    private MockMultipartFile validPdfFile;
    private static final String BUCKET_NAME = "pharmatrust-test-bucket";
    private static final String REGION = "ap-south-1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(storageService, "region", REGION);
        ReflectionTestUtils.setField(storageService, "presignedUrlExpirationMinutes", 15);

        batchId = UUID.randomUUID();
        validPdfFile = new MockMultipartFile(
                "lab-report",
                "lab-report.pdf",
                "application/pdf",
                "PDF content here".getBytes());
    }

    // ==================== Upload Lab Report ====================

    @Test
    void uploadLabReport_validPdf_returnsS3Key() throws IOException {
        when(cryptographyService.generateSHA256Hash(any(byte[].class))).thenReturn("abc123hash");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String s3Key = storageService.uploadLabReport(validPdfFile, batchId);

        assertThat(s3Key).isNotNull();
        assertThat(s3Key).startsWith("lab-reports/" + batchId);
        assertThat(s3Key).endsWith(".pdf");
    }

    @Test
    void uploadLabReport_calculatesHashBeforeUpload() throws IOException {
        when(cryptographyService.generateSHA256Hash(any(byte[].class))).thenReturn("filehash");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.uploadLabReport(validPdfFile, batchId);

        verify(cryptographyService).generateSHA256Hash(any(byte[].class));
    }

    @Test
    void uploadLabReport_uploadsWithSSEEncryption() throws IOException {
        when(cryptographyService.generateSHA256Hash(any(byte[].class))).thenReturn("hash");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.uploadLabReport(validPdfFile, batchId);

        verify(s3Client).putObject(
                argThat((PutObjectRequest req) ->
                        req.serverSideEncryption() == ServerSideEncryption.AES256),
                any(RequestBody.class));
    }

    @Test
    void uploadLabReport_nullFile_throwsException() {
        assertThatThrownBy(() -> storageService.uploadLabReport(null, batchId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void uploadLabReport_emptyFile_throwsException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "empty", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> storageService.uploadLabReport(emptyFile, batchId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadLabReport_nonPdfFile_throwsException() {
        MockMultipartFile txtFile = new MockMultipartFile(
                "report", "report.txt", "text/plain", "text content".getBytes());

        assertThatThrownBy(() -> storageService.uploadLabReport(txtFile, batchId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PDF files are allowed");
    }

    @Test
    void uploadLabReport_fileTooLarge_throwsException() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB > 10MB limit
        MockMultipartFile largeFile = new MockMultipartFile(
                "large", "large.pdf", "application/pdf", largeContent);

        assertThatThrownBy(() -> storageService.uploadLabReport(largeFile, batchId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    // ==================== Verify File Integrity ====================

    @Test
    void verifyFileIntegrity_hashMatches_returnsTrue() {
        String s3Key = "lab-reports/" + batchId + "/report.pdf";
        String expectedHash = "abc123hash";
        byte[] fileBytes = "PDF content".getBytes();

        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(fileBytes);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);
        when(cryptographyService.generateSHA256Hash(fileBytes)).thenReturn(expectedHash);

        boolean result = storageService.verifyFileIntegrity(s3Key, expectedHash);

        assertThat(result).isTrue();
    }

    @Test
    void verifyFileIntegrity_hashMismatch_returnsFalse() {
        String s3Key = "lab-reports/" + batchId + "/report.pdf";
        byte[] fileBytes = "tampered content".getBytes();

        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(fileBytes);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);
        when(cryptographyService.generateSHA256Hash(fileBytes)).thenReturn("different-hash");

        boolean result = storageService.verifyFileIntegrity(s3Key, "expected-hash");

        assertThat(result).isFalse();
    }

    @Test
    void verifyFileIntegrity_nullS3Key_throwsException() {
        assertThatThrownBy(() -> storageService.verifyFileIntegrity(null, "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyFileIntegrity_nullExpectedHash_throwsException() {
        assertThatThrownBy(() -> storageService.verifyFileIntegrity("key", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Delete File ====================

    @Test
    void deleteFile_validKey_callsS3Delete() {
        String s3Key = "lab-reports/" + batchId + "/report.pdf";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storageService.deleteFile(s3Key);

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                req.bucket().equals(BUCKET_NAME) && req.key().equals(s3Key)));
    }

    @Test
    void deleteFile_nullKey_throwsException() {
        assertThatThrownBy(() -> storageService.deleteFile(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteFile_emptyKey_throwsException() {
        assertThatThrownBy(() -> storageService.deleteFile(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== File Exists ====================

    @Test
    void fileExists_existingFile_returnsTrue() {
        String s3Key = "lab-reports/test.pdf";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        boolean exists = storageService.fileExists(s3Key);

        assertThat(exists).isTrue();
    }

    @Test
    void fileExists_nonExistentFile_returnsFalse() {
        String s3Key = "lab-reports/nonexistent.pdf";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        boolean exists = storageService.fileExists(s3Key);

        assertThat(exists).isFalse();
    }

    @Test
    void fileExists_nullKey_returnsFalse() {
        boolean exists = storageService.fileExists(null);

        assertThat(exists).isFalse();
    }
}
