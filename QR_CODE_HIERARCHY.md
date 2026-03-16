# QR Code Hierarchy Documentation

## Overview
PharmaTrust uses a two-level QR code hierarchy for pharmaceutical tracking:
1. **Individual Dose QR Codes** - For each tablet/bottle
2. **Box QR Codes** - For boxes containing 10 doses

---

## QR Code Hierarchy Structure

```
Batch: PAR-20240313-ABC123 (1000 doses)
│
├── Individual Doses (TABLET) - 1000 units
│   ├── PAR-20240313-ABC123-D-000001-HASH (QR Code)
│   ├── PAR-20240313-ABC123-D-000002-HASH (QR Code)
│   ├── PAR-20240313-ABC123-D-000003-HASH (QR Code)
│   └── ... (997 more)
│
└── Boxes (BOX) - 100 boxes
    ├── PAR-20240313-ABC123-BOX-0001-HASH (Parent QR Code)
    │   └── Contains doses 1-10
    ├── PAR-20240313-ABC123-BOX-0002-HASH (Parent QR Code)
    │   └── Contains doses 11-20
    └── ... (98 more boxes)
```

---

## Individual Dose QR Code

### QR Code Data Format
```json
{
  "sn": "PAR-20240313-ABC123-D-000001-HASH",
  "bn": "PAR-20240313-ABC123",
  "mn": "Paracetamol 500mg",
  "mfd": "2024-03-13",
  "exp": "2026-03-13",
  "otp": "87654321",
  "sig": "base64_signature_truncated",
  "ts": 1710345600
}
```

### Field Descriptions
- **sn** (Serial Number): Unique identifier for this dose
- **bn** (Batch Number): Parent batch identifier
- **mn** (Medicine Name): Full medicine name with dosage
- **mfd** (Manufacturing Date): Production date
- **exp** (Expiry Date): Expiration date
- **otp** (TOTP): Time-based one-time password for offline verification
- **sig** (Signature): Digital signature (truncated to 32 chars for QR size)
- **ts** (Timestamp): Unix timestamp when QR was generated

### QR Code Specifications
- **Size**: 200x200 pixels
- **Error Correction**: High (Level H)
- **Encoding**: UTF-8
- **Format**: PNG image, Base64 encoded

### Use Cases
- Patient scans individual bottle/tablet to verify authenticity
- Pharmacist scans to check expiry and counterfeit status
- Offline verification using TOTP (no internet required)

---

## Box QR Code (Parent)

### QR Code Data Format
```json
{
  "psn": "PAR-20240313-ABC123-BOX-0001-HASH",
  "bn": "PAR-20240313-ABC123",
  "cc": 10,
  "mr": "merkle_root_of_10_doses",
  "ts": 1710345600
}
```

### Field Descriptions
- **psn** (Parent Serial Number): Unique identifier for this box
- **bn** (Batch Number): Parent batch identifier
- **cc** (Child Count): Number of doses in this box (always 10)
- **mr** (Merkle Root): Cryptographic hash of all 10 dose signatures
- **ts** (Timestamp): Unix timestamp when QR was generated

### QR Code Specifications
- **Size**: 300x300 pixels (larger for more data)
- **Error Correction**: High (Level H)
- **Encoding**: UTF-8
- **Format**: PNG image, Base64 encoded

### Use Cases
- Distributor scans box to verify all 10 doses are authentic
- Retailer scans to check box integrity before unpacking
- Regulator scans to verify Merkle root matches blockchain data

---

## Serial Number Format

### Individual Dose Serial Number
```
Format: {BATCH_NUMBER}-D-{INDEX}-{HASH}
Example: PAR-20240313-ABC123-D-000001-A1B2C3D4

Components:
- BATCH_NUMBER: Parent batch identifier
- D: Dose indicator
- INDEX: 6-digit sequential number (000001-001000)
- HASH: 8-character SHA-256 hash for uniqueness
```

### Box Serial Number
```
Format: {BATCH_NUMBER}-BOX-{INDEX}-{HASH}
Example: PAR-20240313-ABC123-BOX-0001-E5F6G7H8

Components:
- BATCH_NUMBER: Parent batch identifier
- BOX: Box indicator
- INDEX: 4-digit sequential number (0001-0100)
- HASH: 8-character SHA-256 hash for uniqueness
```

---

## Merkle Root Verification

### What is Merkle Root?
A Merkle root is a cryptographic hash that represents all doses in a box. If any dose is tampered with, the Merkle root will change.

### How It Works
```
Dose 1 Signature: sig1
Dose 2 Signature: sig2
...
Dose 10 Signature: sig10

Level 1: hash(sig1+sig2), hash(sig3+sig4), ..., hash(sig9+sig10)
Level 2: hash(hash1+hash2), hash(hash3+hash4), hash(hash5)
Level 3: hash(hash12+hash34), hash(hash5)
Level 4 (Root): hash(hash1234+hash5)
```

### Verification Process
1. Scan box QR code → Get Merkle root
2. Scan all 10 individual dose QR codes → Get 10 signatures
3. Calculate Merkle root from 10 signatures
4. Compare calculated root with box QR root
5. If match → Box is authentic and intact
6. If mismatch → Box has been tampered with

---

## Database Storage

### Unit Items Table
```sql
CREATE TABLE unit_items (
    id UUID PRIMARY KEY,
    serial_number VARCHAR(255) UNIQUE NOT NULL,
    batch_id UUID NOT NULL,
    parent_unit_id UUID,  -- NULL for doses, points to box for child doses
    unit_type VARCHAR(50),  -- 'TABLET' or 'BOX'
    qr_payload_encrypted TEXT,  -- Base64 encoded QR image
    digital_signature TEXT,
    status VARCHAR(50),
    scan_count INTEGER,
    max_scan_limit INTEGER,
    is_active BOOLEAN,
    current_owner_id UUID,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (parent_unit_id) REFERENCES unit_items(id)
);
```

### Parent-Child Relationship
- **Doses**: `parent_unit_id` is NULL (no parent)
- **Boxes**: `parent_unit_id` is NULL (no parent)
- **Doses in Box**: `parent_unit_id` points to the box UUID

---

## QR Code Generation Performance

### Bulk Generation (1000 doses + 100 boxes)
- **Individual Dose QR**: ~2ms per QR
- **Box QR**: ~3ms per QR
- **Total Time**: ~2.5 seconds for 1100 QR codes
- **Optimization**: Batch database inserts (100 units per batch)

### Parallel Processing
- **Thread Pool**: 4 threads
- **Async QR Generation**: Optional for very large batches
- **Memory Usage**: ~50MB for 1000 QR codes

---

## Security Features

### 1. TOTP for Offline Verification
- **Algorithm**: RFC 6238 TOTP with HMAC-SHA256
- **Time Window**: 30 seconds
- **Clock Skew**: ±30 seconds tolerance
- **Shared Secret**: Stored securely on server

### 2. Digital Signatures
- **Algorithm**: HMAC-SHA256
- **Key**: Batch-specific secret key
- **Purpose**: Prevent QR code forgery

### 3. Merkle Root Integrity
- **Algorithm**: SHA-256 Merkle tree
- **Purpose**: Detect tampering with any dose in a box
- **Verification**: Compare with blockchain data

---

## API Endpoints

### Generate QR Code for Individual Dose
```
POST /api/v1/qr/generate-unit
Request:
{
  "serialNumber": "PAR-20240313-ABC123-D-000001-HASH",
  "batchNumber": "PAR-20240313-ABC123",
  "medicineName": "Paracetamol 500mg",
  "mfgDate": "2024-03-13",
  "expDate": "2026-03-13",
  "digitalSignature": "base64_signature"
}

Response:
{
  "qrCodeBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "serialNumber": "PAR-20240313-ABC123-D-000001-HASH"
}
```

### Generate QR Code for Box
```
POST /api/v1/qr/generate-box
Request:
{
  "boxSerialNumber": "PAR-20240313-ABC123-BOX-0001-HASH",
  "batchNumber": "PAR-20240313-ABC123",
  "childCount": 10,
  "merkleRoot": "abc123def456..."
}

Response:
{
  "qrCodeBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "boxSerialNumber": "PAR-20240313-ABC123-BOX-0001-HASH"
}
```

### Verify Box Integrity
```
POST /api/v1/qr/verify-box
Request:
{
  "boxSerialNumber": "PAR-20240313-ABC123-BOX-0001-HASH",
  "doseSerialNumbers": [
    "PAR-20240313-ABC123-D-000001-HASH",
    "PAR-20240313-ABC123-D-000002-HASH",
    ...
  ]
}

Response:
{
  "isValid": true,
  "merkleRootMatch": true,
  "message": "Box integrity verified successfully"
}
```

---

## Frontend Integration

### Scanning Individual Dose
```javascript
// Patient scans individual bottle
const scanResult = await api.post('/api/v1/scan', {
  serialNumber: 'PAR-20240313-ABC123-D-000001-HASH'
});

if (scanResult.isCounterfeit) {
  alert('⚠️ COUNTERFEIT DETECTED! DO NOT USE!');
} else {
  alert('✅ Authentic medicine verified');
}
```

### Scanning Box
```javascript
// Distributor scans box
const boxScanResult = await api.post('/api/v1/scan/box', {
  boxSerialNumber: 'PAR-20240313-ABC123-BOX-0001-HASH'
});

console.log(`Box contains ${boxScanResult.childCount} doses`);
console.log(`Merkle root: ${boxScanResult.merkleRoot}`);
```

---

## Future Enhancements

1. **Protocol Buffers**: Use Protobuf for 71% QR size reduction
2. **Dynamic QR Codes**: QR codes that change every 30 seconds
3. **NFC Integration**: Tap-to-verify with NFC tags
4. **Augmented Reality**: AR overlay showing medicine info
5. **Multi-Language QR**: QR codes with localized data

---

## Troubleshooting

### QR Code Not Scanning
- **Issue**: QR code too small or damaged
- **Solution**: Use 300x300 size for better scanning

### TOTP Verification Failed
- **Issue**: Clock skew > 30 seconds
- **Solution**: Sync device time with NTP server

### Merkle Root Mismatch
- **Issue**: One or more doses tampered with
- **Solution**: Report to regulator, quarantine box

---

## Contact & Support

For questions about QR code implementation, contact the PharmaTrust development team.
