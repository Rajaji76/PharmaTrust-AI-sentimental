package pharmatrust.manufacturing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Blockchain Token Service
 * 
 * Features:
 * - Mint batch tokens on blockchain (1 batch = 1 token)
 * - Store batch metadata: medicine name, MFG/EXP dates, lab report hash, 
 *   test officer signature, Merkle root
 * - Mock blockchain implementation (replace with real Web3j in production)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlockchainTokenService {

    private final CryptographyService cryptographyService;

    /**
     * Mint batch token on blockchain
     * 
     * Token contains:
     * - Medicine name
     * - Manufacturing date
     * - Expiry date
     * - Lab report hash (SHA-256)
     * - Test officer digital signature
     * - Merkle root of all units
     * 
     * @param batchNumber Batch number
     * @param medicineName Medicine name
     * @param mfgDate Manufacturing date
     * @param expDate Expiry date
     * @param labReportHash SHA-256 hash of lab report
     * @param testOfficerSignature Test officer's digital signature
     * @param merkleRoot Merkle root of all unit signatures
     * @return BlockchainTokenResult with transaction ID and token metadata
     */
    public BlockchainTokenResult mintBatchToken(
            String batchNumber,
            String medicineName,
            LocalDate mfgDate,
            LocalDate expDate,
            String labReportHash,
            String testOfficerSignature,
            String merkleRoot
    ) {
        try {
            log.info("Minting blockchain token for batch: {}", batchNumber);

            // Create token metadata
            BatchTokenMetadata metadata = BatchTokenMetadata.builder()
                .batchNumber(batchNumber)
                .medicineName(medicineName)
                .manufacturingDate(mfgDate.toString())
                .expiryDate(expDate.toString())
                .labReportHash(labReportHash)
                .testOfficerSignature(testOfficerSignature)
                .merkleRoot(merkleRoot)
                .timestamp(System.currentTimeMillis())
                .build();

            // Generate token data hash
            String tokenData = String.format("%s|%s|%s|%s|%s|%s|%s",
                batchNumber, medicineName, mfgDate, expDate, 
                labReportHash, testOfficerSignature, merkleRoot
            );
            String tokenHash = cryptographyService.hashSHA256(tokenData);

            // Mock blockchain transaction
            // In production, use Web3j to interact with Ethereum/Hyperledger
            String txId = generateMockTransactionId();
            String tokenId = generateTokenId(batchNumber);

            log.info("Blockchain token minted successfully: txId={}, tokenId={}", txId, tokenId);

            return BlockchainTokenResult.builder()
                .success(true)
                .transactionId(txId)
                .tokenId(tokenId)
                .tokenHash(tokenHash)
                .metadata(metadata)
                .blockNumber(generateMockBlockNumber())
                .gasUsed(21000L) // Mock gas usage
                .build();

        } catch (Exception e) {
            log.error("Failed to mint blockchain token for batch: {}", batchNumber, e);
            return BlockchainTokenResult.builder()
                .success(false)
                .errorMessage("Token minting failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Verify batch token on blockchain
     * 
     * @param tokenId Token ID
     * @param merkleRoot Expected Merkle root
     * @return true if token is valid and Merkle root matches
     */
    public boolean verifyBatchToken(String tokenId, String merkleRoot) {
        try {
            // Mock verification
            // In production, query blockchain and verify Merkle root
            log.info("Verifying blockchain token: {}", tokenId);
            
            // Simulate blockchain query delay
            Thread.sleep(100);
            
            // Mock verification (always returns true for demo)
            return true;

        } catch (Exception e) {
            log.error("Failed to verify blockchain token: {}", tokenId, e);
            return false;
        }
    }

    /**
     * Generate mock transaction ID
     */
    private String generateMockTransactionId() {
        return "0x" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate token ID from batch number
     */
    private String generateTokenId(String batchNumber) {
        String hash = cryptographyService.hashSHA256(batchNumber);
        return "TOKEN-" + hash.substring(0, 16).toUpperCase();
    }

    /**
     * Generate mock block number
     */
    private long generateMockBlockNumber() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * Batch Token Metadata
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchTokenMetadata {
        private String batchNumber;
        private String medicineName;
        private String manufacturingDate;
        private String expiryDate;
        private String labReportHash;
        private String testOfficerSignature;
        private String merkleRoot;
        private long timestamp;
    }

    /**
     * Blockchain Token Result
     */
    @lombok.Data
    @lombok.Builder
    public static class BlockchainTokenResult {
        private boolean success;
        private String transactionId;
        private String tokenId;
        private String tokenHash;
        private BatchTokenMetadata metadata;
        private Long blockNumber;
        private Long gasUsed;
        private String errorMessage;
    }
}
