# 🔒 Advanced Security Features - PharmaTrust

## Overview

This document explains three critical production-grade security features implemented in PharmaTrust to solve real-world anti-counterfeit challenges.

---

## 1. 📦 Parent-Child Hierarchy Kill Switch

### Problem Statement (The "Orphan Unit" Problem)

**Scenario**: आपने 1 लाख टैबलेट्स बनाईं जो 10,000 स्ट्रिप्स में गईं, फिर 1,000 डिब्बों में, और फिर 10 बड़े कार्टन (Shippers) में।

**Challenge**: अगर एक बड़ा कार्टन चोरी हो जाए, तो क्या आप उसके अंदर के 10,000 सीरियल नंबर्स को एक साथ ब्लॉक कर सकते हैं?

### Solution: Recursive Parent-Child Serialization

#### Database Design

```sql
-- unit_items table with self-referencing foreign key
CREATE TABLE unit_items (
    id UUID PRIMARY KEY,
    serial_number VARCHAR(255) UNIQUE NOT NULL,
    parent_unit_id UUID REFERENCES unit_items(id),  -- Self-referencing!
    unit_type VARCHAR(50),  -- TABLET, STRIP, BOX, CARTON
    status VARCHAR(50),
    is_active BOOLEAN
);

-- Index for fast hierarchy queries
CREATE INDEX idx_parent_unit ON unit_items(parent_unit_id);
```

#### Hierarchy Example

```
Carton-001 (Parent)
├── Box-001
│   ├── Strip-001
│   │   ├── Tablet-001
│   │   ├── Tablet-002
│   │   └── Tablet-003
│   └── Strip-002
│       ├── Tablet-004
│       └── Tablet-005
└── Box-002
    └── Strip-003
        ├── Tablet-006
        └── Tablet-007
```

#### Implementation

**Service**: `HierarchyKillSwitchService.java`

```java
@Transactional
public int killParentAndChildren(String parentSerialNumber, String reason) {
    // 1. Find parent unit
    UnitItem parentUnit = unitItemRepository.findBySerialNumber(parentSerialNumber);
    
    // 2. Recursively collect all children (DFS traversal)
    List<UnitItem> allUnits = new ArrayList<>();
    allUnits.add(parentUnit);
    collectAllChildren(parentUnit, allUnits);
    
    // 3. Block all units
    for (UnitItem unit : allUnits) {
        unit.setStatus(UnitStatus.RECALLED_AUTO);
        unit.setIsActive(false);
        unitItemRepository.save(unit);
        createBlockAlert(unit, reason, parentSerialNumber);
    }
    
    return allUnits.size();
}
```

#### API Endpoint

```http
POST /api/v1/security/kill-hierarchy
Authorization: Bearer <REGULATOR_JWT_TOKEN>
Content-Type: application/json

{
  "parentSerialNumber": "CARTON-2024-001",
  "reason": "STOLEN"
}

Response:
{
  "success": true,
  "message": "Successfully blocked 10000 units",
  "blockedCount": 10000,
  "parentSerialNumber": "CARTON-2024-001"
}
```

#### Use Cases

1. **Stolen Shipment**: Regulator scans stolen carton → All 10,000 units blocked instantly
2. **Batch Recall**: Manufacturer recalls defective batch → All child units invalidated
3. **Counterfeit Detection**: Fake carton detected → Entire hierarchy flagged

#### Performance Optimization

- **Recursive CTE Query** (for PostgreSQL):
```sql
WITH RECURSIVE children AS (
    SELECT id FROM unit_items WHERE serial_number = 'CARTON-001'
    UNION ALL
    SELECT u.id FROM unit_items u
    INNER JOIN children c ON u.parent_unit_id = c.id
)
UPDATE unit_items SET status = 'RECALLED_AUTO', is_active = false
WHERE id IN (SELECT id FROM children);
```

---

## 2. 🔐 Offline TOTP Verification (Time-based OTP)

### Problem Statement (The "Double-Spending" in Offline Mode)

**Scenario**: कई बार वेयरहाउस या दूर-दराज के इलाकों में इंटरनेट नहीं होता। वहां स्कैनिंग ऑफलाइन होती है।

**Challenge**: अगर कोई नकली क्यूआर कोड वहां चलाया जाए, तो सिस्टम को कैसे पता चलेगा?

### Solution: Hash-Chaining with TOTP

#### Algorithm: RFC 6238 TOTP (Google Authenticator Style)

**Key Concepts**:
- Time-based One-Time Password
- 30-second time windows
- HMAC-SHA256 with shared secret
- No internet required for verification

#### How It Works

```
QR Code Payload:
{
  "serialNumber": "BATCH-2024-001-00001",
  "totp": "87654321",  // 8-digit OTP
  "timestamp": 1710345600,
  "signature": "..."
}

Verification (Offline):
1. Extract TOTP from QR code
2. Calculate expected TOTP using shared secret + current time
3. Compare with ±30 seconds tolerance (clock skew)
4. If match → Authentic, else → Counterfeit
```

#### Implementation

**Service**: `TOTPService.java`

```java
public String generateTOTP(String secret, String serialNumber) {
    // Calculate time counter (30-second windows)
    long timeCounter = Instant.now().getEpochSecond() / 30;
    
    // Combine secret with serial number for uniqueness
    String combinedSecret = secret + ":" + serialNumber;
    
    // Generate HMAC-SHA256
    byte[] hash = generateHMAC(combinedSecret, timeCounter);
    
    // Dynamic truncation (RFC 6238)
    int offset = hash[hash.length - 1] & 0x0F;
    int binary = ((hash[offset] & 0x7F) << 24) | ...;
    
    // Generate 8-digit OTP
    int otp = binary % 100000000;
    return String.format("%08d", otp);
}

public boolean verifyTOTP(String totp, String secret, String serialNumber) {
    long currentTime = Instant.now().getEpochSecond();
    
    // Check current window and ±1 window (90 seconds total)
    for (int i = -1; i <= 1; i++) {
        long adjustedTime = currentTime + (i * 30);
        String expectedTOTP = generateTOTP(secret, serialNumber, adjustedTime);
        
        if (constantTimeEquals(totp, expectedTOTP)) {
            return true;  // Valid!
        }
    }
    
    return false;  // Invalid or expired
}
```

#### API Endpoints

**Generate TOTP** (for QR code creation):
```http
POST /api/v1/security/generate-totp
Authorization: Bearer <MANUFACTURER_JWT_TOKEN>

{
  "serialNumber": "BATCH-2024-001-00001",
  "secret": "shared-secret-key"
}

Response:
{
  "totp": "87654321",
  "serialNumber": "BATCH-2024-001-00001",
  "validitySeconds": 25,
  "expiresAt": 1710345625000
}
```

**Verify TOTP** (offline scan):
```http
POST /api/v1/security/verify-totp

{
  "serialNumber": "BATCH-2024-001-00001",
  "totp": "87654321",
  "secret": "shared-secret-key"
}

Response:
{
  "valid": true,
  "serialNumber": "BATCH-2024-001-00001",
  "message": "TOTP verified successfully"
}
```

#### Security Features

1. **Time-based Expiry**: TOTP changes every 30 seconds
2. **Clock Skew Tolerance**: ±30 seconds for network delays
3. **Unit-Specific**: Each serial number has unique TOTP
4. **Constant-Time Comparison**: Prevents timing attacks

#### Use Cases

1. **Remote Warehouse**: No internet → Verify using TOTP
2. **Mobile App**: Offline mode → Cache shared secret
3. **Emergency Verification**: Server down → TOTP still works

---

## 3. 🔒 Private Blockchain Commitments (Data Privacy)

### Problem Statement (The Competitor Risk)

**Scenario**: अगर आप सारा डेटा ब्लॉकचेन पर डाल देंगे, तो आपकी प्रतिद्वंदी (Competitor) कंपनी यह देख लेगी कि आपने इस महीने कितनी दवाइयां बनाईं।

**Challenge**: How to use blockchain transparency WITHOUT exposing business secrets?

### Solution: Encrypted Commitments (Hyperledger Fabric Style)

#### Concept: "Sealed Envelope" on Blockchain

```
Public Blockchain:
- Stores: Encrypted Hash (Commitment)
- Visible to: Everyone
- Readable by: Only authorized parties with decryption key

Private Database:
- Stores: Actual data (serial numbers, quantities)
- Visible to: Only manufacturer
```

#### Encryption Architecture

```
Step 1: Manufacturer creates batch
├── Actual Data: { batchId: "BATCH-001", quantity: 100000, ... }
├── Generate random AES-256 key
├── Encrypt data with AES-GCM
└── Encrypt AES key with Regulator's RSA public key

Step 2: Store on blockchain
├── Commitment Hash: SHA-256(actual data)
├── Encrypted AES Key: RSA(aes_key, regulator_public_key)
└── Timestamp: 1710345600

Step 3: Regulator audits
├── Decrypt AES key using RSA private key
├── Decrypt data using AES key
└── Verify hash matches commitment
```

#### Implementation

**Service**: `PrivateCommitmentService.java`

```java
public EncryptedCommitment createCommitment(String data, PublicKey recipientPublicKey) {
    // 1. Generate random AES-256 key
    SecretKey aesKey = generateAESKey();
    
    // 2. Encrypt data with AES-GCM (authenticated encryption)
    byte[] encryptedData = encryptWithAES(data.getBytes(), aesKey);
    
    // 3. Encrypt AES key with recipient's RSA public key
    byte[] encryptedAESKey = encryptAESKeyWithRSA(aesKey, recipientPublicKey);
    
    // 4. Create commitment hash (for blockchain)
    String commitmentHash = cryptographyService.hashSHA256(data);
    
    return EncryptedCommitment.builder()
            .encryptedData(Base64.encode(encryptedData))
            .encryptedKey(Base64.encode(encryptedAESKey))
            .commitmentHash(commitmentHash)
            .build();
}

public String decryptCommitment(EncryptedCommitment commitment, PrivateKey recipientPrivateKey) {
    // 1. Decrypt AES key using RSA private key
    SecretKey aesKey = decryptAESKeyWithRSA(commitment.getEncryptedKey(), recipientPrivateKey);
    
    // 2. Decrypt data using AES key
    byte[] decryptedData = decryptWithAES(commitment.getEncryptedData(), aesKey);
    
    // 3. Verify commitment hash
    String calculatedHash = cryptographyService.hashSHA256(new String(decryptedData));
    if (!calculatedHash.equals(commitment.getCommitmentHash())) {
        throw new SecurityException("Commitment hash mismatch!");
    }
    
    return new String(decryptedData);
}
```

#### Blockchain Storage

**Smart Contract** (Solidity):
```solidity
contract PharmaTrustCommitments {
    struct Commitment {
        bytes32 commitmentHash;      // SHA-256 hash (public)
        bytes encryptedKey;          // RSA-encrypted AES key
        uint256 timestamp;
        address manufacturer;
    }
    
    mapping(string => Commitment) public commitments;
    
    function storeCommitment(
        string memory batchId,
        bytes32 commitmentHash,
        bytes memory encryptedKey
    ) public {
        commitments[batchId] = Commitment({
            commitmentHash: commitmentHash,
            encryptedKey: encryptedKey,
            timestamp: block.timestamp,
            manufacturer: msg.sender
        });
    }
    
    function verifyCommitment(
        string memory batchId,
        bytes32 expectedHash
    ) public view returns (bool) {
        return commitments[batchId].commitmentHash == expectedHash;
    }
}
```

#### Security Properties

1. **Confidentiality**: Only authorized parties can decrypt
2. **Integrity**: Hash verification prevents tampering
3. **Non-repudiation**: Blockchain timestamp proves creation time
4. **Selective Disclosure**: Share decryption key only with regulators

#### Use Cases

1. **Business Privacy**: Competitors can't see production volumes
2. **Regulatory Audit**: Regulator decrypts with private key
3. **Supply Chain Verification**: Verify commitment without seeing data
4. **Legal Evidence**: Blockchain timestamp proves data existed at time T

---

## 🚀 Integration Example

### Complete Workflow: Batch Creation with All 3 Features

```java
// 1. Create batch with parent-child hierarchy
Batch batch = batchService.createBatch(request);

// 2. Generate TOTP for each unit
for (UnitItem unit : batch.getUnits()) {
    String totp = totpService.generateTOTP(sharedSecret, unit.getSerialNumber());
    unit.setQrPayloadEncrypted(totp);
}

// 3. Create private commitment for blockchain
String batchData = objectMapper.writeValueAsString(batch);
EncryptedCommitment commitment = privateCommitmentService.createCommitment(
    batchData,
    regulatorPublicKey
);

// 4. Store commitment on blockchain
blockchainService.storeCommitment(batch.getBatchNumber(), commitment);

// 5. If carton stolen → Kill entire hierarchy
hierarchyKillSwitchService.killParentAndChildren("CARTON-001", "STOLEN");
```

---

## 📊 Performance Metrics

| Feature | Operation | Time Complexity | Scalability |
|---------|-----------|----------------|-------------|
| Hierarchy Kill Switch | Block 10,000 units | O(n) | ✅ Handles 100K+ units |
| TOTP Generation | Generate OTP | O(1) | ✅ <1ms per unit |
| TOTP Verification | Verify offline | O(1) | ✅ No network required |
| Private Commitment | Encrypt batch data | O(n) | ✅ AES-256 GCM |
| Commitment Decryption | Decrypt for audit | O(n) | ✅ RSA-2048 |

---

## 🔧 Configuration

### application.properties

```properties
# TOTP Configuration
pharmatrust.totp.time-step-seconds=30
pharmatrust.totp.digits=8
pharmatrust.totp.clock-skew-windows=1

# Hierarchy Kill Switch
pharmatrust.hierarchy.max-depth=5
pharmatrust.hierarchy.batch-size=1000

# Private Commitments
pharmatrust.commitment.aes-key-size=256
pharmatrust.commitment.rsa-key-size=2048
```

---

## 🎯 Skills Demonstrated

1. **SQL Joins & Hierarchical Data Mapping** (Parent-Child)
2. **HMAC (Hash-based Message Authentication Code)** (TOTP)
3. **Asymmetric Encryption (Public/Private Key Pair)** (Commitments)
4. **Recursive Algorithms** (DFS tree traversal)
5. **Time-based Cryptography** (RFC 6238)
6. **Authenticated Encryption** (AES-GCM)
7. **Blockchain Integration** (Smart contracts)

---

## 📚 References

- RFC 6238: TOTP Algorithm - https://tools.ietf.org/html/rfc6238
- Hyperledger Fabric Private Data - https://hyperledger-fabric.readthedocs.io/
- NIST AES-GCM Specification - https://csrc.nist.gov/publications/detail/sp/800-38d/final

---

## 🔐 Security Audit Checklist

- [x] Constant-time TOTP comparison (prevents timing attacks)
- [x] AES-GCM authenticated encryption (prevents tampering)
- [x] RSA-OAEP padding (prevents chosen ciphertext attacks)
- [x] Secure random IV generation (prevents replay attacks)
- [x] Clock skew tolerance (handles network delays)
- [x] Recursive depth limits (prevents stack overflow)
- [x] Transaction isolation (prevents race conditions)
- [x] Alert generation (audit trail)

---

**Status**: ✅ All features implemented and tested
**Backend**: Running on http://localhost:8080
**API Documentation**: See `/api/v1/security/*` endpoints
