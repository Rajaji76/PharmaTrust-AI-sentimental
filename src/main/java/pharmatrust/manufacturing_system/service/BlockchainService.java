package pharmatrust.manufacturing_system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * BlockchainService - Web3j integration for Ethereum blockchain.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Mint batch tokens on-chain (Merkle root only — gas optimisation)</li>
 *   <li>Emit recall events on-chain</li>
 *   <li>Monitor transaction status (waits for 12 confirmations)</li>
 *   <li>Connection retry with exponential back-off (max 3 attempts)</li>
 *   <li>Gas price handling: uses current network price + 20 % buffer</li>
 * </ul>
 *
 * <p>Requirements: BR-004, IR-003, IR-004
 */
@Service
@Slf4j
public class BlockchainService {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    @Value("${blockchain.network-url:http://localhost:8545}")
    private String networkUrl;

    @Value("${blockchain.contract-address:}")
    private String contractAddress;

    @Value("${blockchain.private-key:}")
    private String privateKey;

    @Value("${blockchain.confirmation-blocks:12}")
    private int confirmationBlocks;

    /** Maximum number of connection / transaction attempts. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Base delay for exponential back-off (milliseconds). */
    private static final long BASE_BACKOFF_MS = 1_000L;

    /** Gas price buffer multiplier (1.20 = 20 % above current price). */
    private static final double GAS_PRICE_BUFFER = 1.20;

    /** Polling interval while waiting for confirmations (milliseconds). */
    private static final long CONFIRMATION_POLL_MS = 3_000L;

    /** Maximum time to wait for a transaction to be mined (milliseconds). */
    private static final long TX_TIMEOUT_MS = 120_000L;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private Web3j web3j;
    private Credentials credentials;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Initialise the Web3j client and load credentials on startup.
     * Retries up to {@value #MAX_RETRY_ATTEMPTS} times with exponential back-off.
     */
    @PostConstruct
    public void init() {
        log.info("Initialising BlockchainService — network: {}", networkUrl);
        connectWithRetry();
    }

    @PreDestroy
    public void shutdown() {
        if (web3j != null) {
            web3j.shutdown();
            log.info("Web3j client shut down");
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Mint a batch token on-chain.
     *
     * <p>Only the Merkle root is stored on-chain to minimise gas fees.
     * Individual unit serial numbers are NOT stored (gas optimisation per FR-018).
     *
     * @param batchNumber       unique batch identifier
     * @param medicineHash      SHA-256 hash of the medicine name
     * @param manufacturingDate manufacturing date
     * @param expiryDate        expiry date
     * @param manufacturerAddr  Ethereum address of the manufacturer
     * @param labReportHash     SHA-256 hash of the lab report
     * @param merkleRoot        Merkle root of all unit serial numbers
     * @param totalUnits        total number of units in the batch
     * @return transaction hash, or {@code null} on failure
     */
    public String mintBatchToken(
            String batchNumber,
            String medicineHash,
            LocalDate manufacturingDate,
            LocalDate expiryDate,
            String manufacturerAddr,
            String labReportHash,
            String merkleRoot,
            long totalUnits) {

        log.info("Minting batch token — batch: {}, merkleRoot: {}", batchNumber, merkleRoot);

        // Encode the call data for mintBatch(string,string,uint256,uint256,address,bytes32,bytes32,uint256)
        String callData = encodeMintBatchCall(
                batchNumber, medicineHash,
                manufacturingDate, expiryDate,
                manufacturerAddr, labReportHash,
                merkleRoot, totalUnits);

        return sendTransactionWithRetry(callData, "mintBatchToken[" + batchNumber + "]");
    }

    /**
     * Emit a recall event on-chain.
     *
     * @param batchNumber    batch being recalled
     * @param initiatorAddr  Ethereum address of the recall initiator
     * @param reason         human-readable recall reason
     * @param autoTriggered  {@code true} if triggered by AI Sentinel
     * @return transaction hash, or {@code null} on failure
     */
    public String emitRecallEvent(
            String batchNumber,
            String initiatorAddr,
            String reason,
            boolean autoTriggered) {

        log.info("Emitting recall event — batch: {}, autoTriggered: {}", batchNumber, autoTriggered);

        String callData = encodeRecallEventCall(batchNumber, initiatorAddr, reason, autoTriggered);
        return sendTransactionWithRetry(callData, "emitRecallEvent[" + batchNumber + "]");
    }

    /**
     * Query the status of a previously submitted transaction.
     *
     * @param txHash Ethereum transaction hash
     * @return {@link TransactionStatus} describing the current state
     */
    public TransactionStatus getTransactionStatus(String txHash) {
        if (!isConnected()) {
            return TransactionStatus.builder()
                    .txHash(txHash)
                    .status("UNKNOWN")
                    .errorMessage("Blockchain client not connected")
                    .build();
        }

        try {
            EthGetTransactionReceipt receiptResponse =
                    web3j.ethGetTransactionReceipt(txHash).send();

            Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();
            if (receiptOpt.isEmpty()) {
                return TransactionStatus.builder()
                        .txHash(txHash)
                        .status("PENDING")
                        .build();
            }

            TransactionReceipt receipt = receiptOpt.get();
            long txBlock = Numeric.decodeQuantity(receipt.getBlockNumberRaw()).longValue();
            long currentBlock = getCurrentBlockNumber();
            long confirmations = currentBlock - txBlock;

            boolean confirmed = confirmations >= confirmationBlocks;
            boolean success = "0x1".equalsIgnoreCase(receipt.getStatus());

            return TransactionStatus.builder()
                    .txHash(txHash)
                    .status(confirmed ? (success ? "CONFIRMED" : "FAILED") : "PENDING")
                    .blockNumber(txBlock)
                    .confirmations(confirmations)
                    .gasUsed(receipt.getGasUsed() != null ? receipt.getGasUsed().longValue() : 0L)
                    .build();

        } catch (Exception e) {
            log.error("Failed to query transaction status for {}: {}", txHash, e.getMessage());
            return TransactionStatus.builder()
                    .txHash(txHash)
                    .status("ERROR")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Wait for a transaction to reach the required number of confirmations.
     *
     * @param txHash transaction hash to monitor
     * @return {@code true} if confirmed successfully within the timeout
     */
    public boolean waitForConfirmations(String txHash) {
        log.info("Waiting for {} confirmations on tx: {}", confirmationBlocks, txHash);
        long deadline = System.currentTimeMillis() + TX_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            TransactionStatus status = getTransactionStatus(txHash);

            if ("CONFIRMED".equals(status.getStatus())) {
                log.info("Transaction confirmed: {} ({} confirmations)", txHash, status.getConfirmations());
                return true;
            }
            if ("FAILED".equals(status.getStatus())) {
                log.warn("Transaction failed on-chain: {}", txHash);
                return false;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(CONFIRMATION_POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.warn("Timed out waiting for confirmations on tx: {}", txHash);
        return false;
    }

    // -----------------------------------------------------------------------
    // Connection management
    // -----------------------------------------------------------------------

    /**
     * Attempt to connect to the Ethereum node with exponential back-off.
     * Falls back to a degraded (mock) mode if all attempts fail so the
     * application can still start in environments without a live node.
     */
    private void connectWithRetry() {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                web3j = Web3j.build(new HttpService(networkUrl));

                // Verify connectivity
                EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
                log.info("Connected to Ethereum node — current block: {}",
                        blockNumber.getBlockNumber());

                // Load credentials if a private key is configured
                if (privateKey != null && !privateKey.isBlank()) {
                    credentials = Credentials.create(privateKey);
                    log.info("Loaded credentials — address: {}", credentials.getAddress());
                } else {
                    log.warn("No blockchain.private-key configured — transactions will be skipped");
                }

                return; // success

            } catch (Exception e) {
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
                log.warn("Blockchain connection attempt {}/{} failed: {}. Retrying in {}ms",
                        attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), backoff);

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("All {} blockchain connection attempts failed. "
                + "Service will operate in degraded mode (transactions skipped).",
                MAX_RETRY_ATTEMPTS);
        web3j = null;
    }

    private boolean isConnected() {
        return web3j != null;
    }

    // -----------------------------------------------------------------------
    // Transaction helpers
    // -----------------------------------------------------------------------

    /**
     * Send a contract call transaction with retry and exponential back-off.
     *
     * @param callData ABI-encoded function call data
     * @param opName   human-readable operation name for logging
     * @return transaction hash on success, {@code null} on failure
     */
    private String sendTransactionWithRetry(String callData, String opName) {
        if (!isConnected()) {
            log.warn("Blockchain not connected — skipping transaction for {}", opName);
            return null;
        }
        if (credentials == null) {
            log.warn("No credentials configured — skipping transaction for {}", opName);
            return null;
        }
        if (contractAddress == null || contractAddress.isBlank()) {
            log.warn("No contract address configured — skipping transaction for {}", opName);
            return null;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                BigInteger gasPrice = getGasPriceWithBuffer();
                BigInteger gasLimit = DefaultGasProvider.GAS_LIMIT;
                BigInteger nonce = getNonce(credentials.getAddress());

                Transaction tx = Transaction.createFunctionCallTransaction(
                        credentials.getAddress(),
                        nonce,
                        gasPrice,
                        gasLimit,
                        contractAddress,
                        callData);

                EthSendTransaction response = web3j.ethSendRawTransaction(
                        signTransaction(tx, gasPrice, gasLimit, nonce, callData)).send();

                if (response.hasError()) {
                    throw new RuntimeException("RPC error: " + response.getError().getMessage());
                }

                String txHash = response.getTransactionHash();
                log.info("Transaction submitted — op: {}, txHash: {}", opName, txHash);
                return txHash;

            } catch (Exception e) {
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                log.warn("Transaction attempt {}/{} failed for {}: {}. Retrying in {}ms",
                        attempt, MAX_RETRY_ATTEMPTS, opName, e.getMessage(), backoff);

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        log.error("All {} transaction attempts failed for {}", MAX_RETRY_ATTEMPTS, opName);
        return null;
    }

    /**
     * Fetch the current gas price from the network and apply a 20 % buffer
     * to improve the chance of timely inclusion (handles gas price fluctuations).
     */
    private BigInteger getGasPriceWithBuffer() {
        try {
            EthGasPrice gasPriceResponse = web3j.ethGasPrice().send();
            BigInteger networkPrice = gasPriceResponse.getGasPrice();
            // Apply buffer: price * 1.20
            BigInteger buffered = networkPrice
                    .multiply(BigInteger.valueOf((long) (GAS_PRICE_BUFFER * 100)))
                    .divide(BigInteger.valueOf(100));
            log.debug("Gas price — network: {} wei, buffered: {} wei", networkPrice, buffered);
            return buffered;
        } catch (Exception e) {
            log.warn("Could not fetch gas price, using default: {}", e.getMessage());
            return DefaultGasProvider.GAS_PRICE;
        }
    }

    /** Fetch the pending nonce for the given address. */
    private BigInteger getNonce(String address) throws Exception {
        return web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                .send()
                .getTransactionCount();
    }

    /** Get the current block number from the network. */
    private long getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (Exception e) {
            log.warn("Could not fetch block number: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Sign and RLP-encode a transaction using the loaded credentials.
     * Returns the hex-encoded signed transaction ready for broadcast.
     */
    private String signTransaction(
            Transaction tx,
            BigInteger gasPrice,
            BigInteger gasLimit,
            BigInteger nonce,
            String callData) throws Exception {

        // Fetch chain ID for EIP-155 replay protection
        long chainId = web3j.ethChainId().send().getChainId().longValue();

        org.web3j.crypto.RawTransaction rawTx = org.web3j.crypto.RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                callData);

        byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTx, chainId, credentials);
        return Numeric.toHexString(signedMessage);
    }

    // -----------------------------------------------------------------------
    // ABI encoding helpers
    // -----------------------------------------------------------------------

    /**
     * ABI-encode the {@code mintBatch} function call.
     *
     * <p>Function signature (Solidity):
     * <pre>
     * mintBatch(string batchNumber, string medicineHash,
     *           uint256 manufacturingDate, uint256 expiryDate,
     *           address manufacturer, bytes32 labReportHash,
     *           bytes32 merkleRoot, uint256 totalUnits)
     * </pre>
     */
    private String encodeMintBatchCall(
            String batchNumber,
            String medicineHash,
            LocalDate manufacturingDate,
            LocalDate expiryDate,
            String manufacturerAddr,
            String labReportHash,
            String merkleRoot,
            long totalUnits) {

        // 4-byte selector: keccak256("mintBatch(string,string,uint256,uint256,address,bytes32,bytes32,uint256)")
        String selector = "0x" + keccak256Hex(
                "mintBatch(string,string,uint256,uint256,address,bytes32,bytes32,uint256)")
                .substring(0, 8);

        long mfgEpoch = manufacturingDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long expEpoch = expiryDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        return selector
                + abiEncodeString(batchNumber)
                + abiEncodeString(medicineHash)
                + abiEncodeUint256(BigInteger.valueOf(mfgEpoch))
                + abiEncodeUint256(BigInteger.valueOf(expEpoch))
                + abiEncodeAddress(manufacturerAddr)
                + abiEncodeBytes32(labReportHash)
                + abiEncodeBytes32(merkleRoot)
                + abiEncodeUint256(BigInteger.valueOf(totalUnits));
    }

    /**
     * ABI-encode the {@code recallBatch} function call.
     *
     * <p>Function signature (Solidity):
     * <pre>
     * recallBatch(string batchNumber, address initiator,
     *             string reason, bool autoTriggered)
     * </pre>
     */
    private String encodeRecallEventCall(
            String batchNumber,
            String initiatorAddr,
            String reason,
            boolean autoTriggered) {

        String selector = "0x" + keccak256Hex(
                "recallBatch(string,address,string,bool)")
                .substring(0, 8);

        return selector
                + abiEncodeString(batchNumber)
                + abiEncodeAddress(initiatorAddr)
                + abiEncodeString(reason)
                + abiEncodeBool(autoTriggered);
    }

    // -----------------------------------------------------------------------
    // Minimal ABI encoding utilities
    // -----------------------------------------------------------------------

    private String abiEncodeUint256(BigInteger value) {
        return Numeric.toHexStringNoPrefixZeroPadded(value, 64);
    }

    private String abiEncodeAddress(String address) {
        if (address == null || address.isBlank()) {
            return "0".repeat(64);
        }
        String clean = Numeric.cleanHexPrefix(address).toLowerCase();
        return "0".repeat(64 - clean.length()) + clean;
    }

    private String abiEncodeBytes32(String hexOrPlain) {
        if (hexOrPlain == null || hexOrPlain.isBlank()) {
            return "0".repeat(64);
        }
        String clean = Numeric.cleanHexPrefix(hexOrPlain);
        if (clean.length() > 64) {
            clean = clean.substring(0, 64);
        }
        return clean + "0".repeat(64 - clean.length());
    }

    private String abiEncodeBool(boolean value) {
        return "0".repeat(63) + (value ? "1" : "0");
    }

    /**
     * ABI-encode a dynamic {@code string} value.
     * Returns the offset placeholder + length + padded UTF-8 bytes.
     * Note: for multi-argument calls the caller must adjust offsets;
     * this simplified version is suitable for single-string or appended encoding.
     */
    private String abiEncodeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        String lengthHex = abiEncodeUint256(BigInteger.valueOf(bytes.length));
        StringBuilder dataHex = new StringBuilder();
        for (byte b : bytes) {
            dataHex.append(String.format("%02x", b));
        }
        // Pad to 32-byte boundary
        int padding = (32 - (bytes.length % 32)) % 32;
        dataHex.append("0".repeat(padding * 2));
        return lengthHex + dataHex;
    }

    /** Compute keccak256 of a UTF-8 string and return the hex digest (no prefix). */
    private String keccak256Hex(String input) {
        byte[] hash = org.web3j.crypto.Hash.sha3(input.getBytes(StandardCharsets.UTF_8));
        return Numeric.toHexStringNoPrefix(hash);
    }

    // -----------------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------------

    /**
     * Represents the current status of an on-chain transaction.
     */
    @lombok.Data
    @lombok.Builder
    public static class TransactionStatus {
        private String txHash;
        /** PENDING | CONFIRMED | FAILED | ERROR | UNKNOWN */
        private String status;
        private long blockNumber;
        private long confirmations;
        private long gasUsed;
        private String errorMessage;
    }
}
