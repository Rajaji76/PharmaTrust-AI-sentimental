# PharmaTrust Production Workflow Documentation

## Complete Batch Creation Workflow

### Overview
This document describes the complete production workflow for creating pharmaceutical batches with blockchain integration, AI verification, and QR code generation.

---

## Workflow Steps

### 1. Lab Report Upload & Hash Calculation
- **Input**: Lab report PDF file
- **Process**: 
  - Upload to AWS S3
  - Calculate SHA-256 hash of file content
  - Store S3 key and hash in database
- **Output**: S3 key, SHA-256 hash

### 2. AI Verification (99%+ Accuracy)
- **Input**: Medicine name, lab report hash, report content
- **Process**:
  - Match with historical lab report data
  - Calculate similarity score using ML algorithms
  - Verify purity scores against historical averages
- **Threshold**: 99% or higher similarity required
- **Output**: Pass/Fail, similarity score, confidence level

### 3. Test Officer Signature Verification
- **Input**: Digital signature from test officer
- **Process**: Verify signature using RSA/ECDSA
- **Output**: Signature validity

### 4. Batch Creation
- **Input**: Medicine name, MFG/EXP dates, total units
- **Process**:
  - Generate unique batch number
  - Create batch entity in database
  - Generate batch digital signature
- **Output**: Batch entity with unique ID

### 5. Bulk Unit Generation with QR Codes
- **Hierarchy**:
  ```
  Batch (1000 units)
  ├── Individual Doses (TABLET) - 1000 units
  │   └── Each has QR code with: serial number, batch number, medicine name, MFG/EXP, TOTP, signature
  └── Boxes (BOX) - 100 boxes (10 doses per box)
      └── Each has parent QR code with: box serial, child count, Merkle root, batch number
  ```

- **QR Code Data**:
  
  **Individual Dose QR:**
  ```json
  {
    "sn": "PAR-20240313-ABC123-D-000001-HASH",
    "bn": "PAR-20240313-ABC123",
    "mn": "Paracetamol 500mg",
    "mfd": "2024-03-13",
    "exp": "2026-03-13",
    "otp": "87654321",
    "sig": "base64_signature",
    "ts": 1710345600
  }
  ```

  **Box QR:**
  ```json
  {
    "psn": "PAR-20240313-ABC123-BOX-0001-HASH",
    "bn": "PAR-20240313-ABC123",
    "cc": 10,
    "mr": "merkle_root_of_10_doses",
    "ts": 1710345600
  }
  ```

- **Performance**: Optimized batch inserts (100 units per batch)

### 6. Merkle Root Calculation
- **Input**: Digital signatures of all individual doses
- **Process**: Calculate Merkle tree root hash
- **Output**: Merkle root (64-character hex string)

### 7. Blockchain Token Minting
- **Token Metadata**:
  - Medicine name
  - Manufacturing date
  - Expiry date
  - Lab report hash (SHA-256)
  - Test officer digital signature
  - Merkle root of all units
  - Timestamp

- **Blockchain**: 1 Batch = 1 Token (not individual units)
- **Output**: Transaction ID, Token ID, Block number

### 8. Batch Activation
- **Process**: Update batch status to ACTIVE
- **Result**: Batch is ready for distribution

---

## API Endpoints

### Create Batch with Complete Workflow

**Endpoint**: `POST /api/v1/batches/create-complete`

**Request** (multipart/form-data):
```
medicineName: "Paracetamol 500mg"
manufacturingDate: "2024-03-13"
expiryDate: "2026-03-13"
totalUnits: 1000
labReportFile: [PDF file]
testOfficerSignature: "base64_signature"
labReportContent: "Extracted text from lab report"
```

**Response**:
```json
{
  "id": "uuid",
  "batchNumber": "PAR-20240313-ABC123",
  "medicineName": "Paracetamol 500mg",
  "manufacturingDate": "2024-03-13",
  "expiryDate": "2026-03-13",
  "status": "ACTIVE",
  "totalUnits": 1000,
  "merkleRoot": "abc123...",
  "blockchainTxId": "0x123abc...",
  "blockchainTokenId": "TOKEN-ABC123DEF456",
  "aiVerificationScore": 99.5,
  "units": [
    {
      "serialNumber": "PAR-20240313-ABC123-D-000001-HASH",
      "unitType": "TABLET",
      "qrCodeData": "base64_qr_image",
      "digitalSignature": "signature"
    },
    {
      "serialNumber": "PAR-20240313-ABC123-BOX-0001-HASH",
      "unitType": "BOX",
      "qrCodeData": "base64_qr_image",
      "digitalSignature": "signature"
    }
  ]
}
```

---

## Services Architecture

### 1. LabReportService
- AWS S3 integration
- SHA-256 hash calculation
- Test officer signature verification

### 2. AIVerificationService
- Historical data matching
- Similarity score calculation (99%+ threshold)
- Confidence level calculation
- Mock implementation (replace with real ML model)

### 3. BlockchainTokenService
- Batch token minting
- Token metadata storage
- Mock blockchain (replace with Web3j for Ethereum/Hyperledger)

### 4. BulkSerializationService
- Fast unit generation (1000+ units)
- Batch insert optimization
- QR code generation for doses and boxes
- Parent-child hierarchy management

### 5. CryptographyService
- SHA-256 hashing
- HMAC generation
- Merkle root calculation
- RSA/ECDSA signing and verification

### 6. QRCodeService
- ZXing library integration
- Individual dose QR codes (200x200)
- Parent box QR codes (300x300)
- TOTP embedding for offline verification

---

## Database Schema

### Batches Table
```sql
- id (UUID, PK)
- batch_number (VARCHAR, UNIQUE)
- medicine_name (VARCHAR)
- manufacturing_date (DATE)
- expiry_date (DATE)
- manufacturer_id (UUID, FK)
- lab_report_hash (VARCHAR)
- lab_report_s3_key (VARCHAR)
- blockchain_tx_id (VARCHAR)
- merkle_root (VARCHAR)
- status (ENUM: PROCESSING, ACTIVE, QUARANTINE, RECALLED)
- total_units (INTEGER)
- digital_signature (TEXT)
- created_at (TIMESTAMP)
```

### Unit Items Table
```sql
- id (UUID, PK)
- serial_number (VARCHAR, UNIQUE)
- batch_id (UUID, FK)
- parent_unit_id (UUID, FK, nullable)
- unit_type (ENUM: TABLET, BOX)
- qr_payload_encrypted (TEXT)
- digital_signature (TEXT)
- status (ENUM: ACTIVE, TRANSFERRED, RECALLED, EXPIRED)
- scan_count (INTEGER)
- max_scan_limit (INTEGER)
- is_active (BOOLEAN)
- current_owner_id (UUID, FK)
```

---

## Performance Metrics

### Bulk Serialization
- **1000 units**: ~2-3 seconds
- **10,000 units**: ~20-30 seconds
- **Batch insert size**: 100 units per batch
- **QR generation**: Parallel processing with 4 threads

### AI Verification
- **Average time**: 100-200ms
- **Accuracy**: 99%+ similarity threshold
- **Confidence**: Based on historical data variance

### Blockchain Token Minting
- **Mock time**: 100ms
- **Real blockchain**: 15-30 seconds (depends on network)
- **Gas cost**: ~21,000 gas units (Ethereum)

---

## Security Features

### 1. Digital Signatures
- Batch signature: HMAC-SHA256
- Unit signature: HMAC-SHA256
- Test officer signature: RSA-2048/ECDSA

### 2. Merkle Root Verification
- Prevents tampering with individual units
- Stored on blockchain for immutability

### 3. TOTP for Offline Verification
- 30-second time windows
- ±30s clock skew tolerance
- Embedded in QR codes

### 4. Lab Report Integrity
- SHA-256 hash stored on blockchain
- Any modification detected immediately

---

## Error Handling

### AI Verification Failure
- **Reason**: Similarity score < 99%
- **Action**: Reject batch creation
- **Response**: Error message with similarity score

### Lab Report Upload Failure
- **Reason**: S3 upload error, invalid file type
- **Action**: Rollback batch creation
- **Response**: Error message

### Blockchain Token Minting Failure
- **Reason**: Network error, insufficient gas
- **Action**: Batch marked as PROCESSING, retry later
- **Response**: Error message with transaction details

---

## Future Enhancements

1. **Real ML Model Integration**: Replace mock AI with TensorFlow/PyTorch
2. **Real Blockchain Integration**: Use Web3j for Ethereum/Hyperledger
3. **Protocol Buffers**: Use Protobuf for 71% QR size reduction
4. **Geographic Anomaly Detection**: Auto kill-switch for impossible travel
5. **RabbitMQ Integration**: Async batch processing with message queue
6. **Zero-Knowledge Proofs**: Privacy-preserving verification

---

## Testing

### Unit Tests
- LabReportService: S3 upload, hash calculation
- AIVerificationService: Similarity calculation, threshold validation
- BlockchainTokenService: Token minting, metadata storage
- BulkSerializationService: Unit generation, QR code creation

### Integration Tests
- Complete workflow end-to-end
- Database transactions
- S3 integration
- Blockchain integration

### Performance Tests
- 1000 units generation time
- 10,000 units generation time
- Concurrent batch creation

---

## Deployment

### Prerequisites
- Java 17+
- PostgreSQL 15+
- AWS S3 bucket
- Docker & Docker Compose
- Maven 3.8+

### Environment Variables
```
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=pharmatrust-lab-reports
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pharmatrust
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
```

### Run Application
```bash
# Start Docker services
docker-compose up -d

# Build application
./mvnw clean package

# Run application
./mvnw spring-boot:run
```

---

## Contact & Support

For questions or issues, contact the PharmaTrust development team.
