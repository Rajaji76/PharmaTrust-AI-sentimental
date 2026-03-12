# Requirements Specification: PharmaTrust Production Upgrade

## 1. Executive Summary

This document outlines the functional and non-functional requirements for transforming PharmaTrust-AI from a prototype pharmaceutical tracking system to a production-ready enterprise platform. The upgrade addresses critical security, scalability, and compliance needs for pharmaceutical supply chain integrity.

## 2. Business Requirements

### 2.1 Core Business Objectives

**BR-001: Enterprise Database Migration**
- Migrate from SQLite to PostgreSQL for production-grade data management
- Support concurrent access from multiple users and systems
- Enable horizontal scaling for future growth
- Priority: Critical

**BR-002: Cloud Storage Integration**
- Replace local file storage with AWS S3 for lab reports
- Ensure 99.99% availability of critical documents
- Enable secure access from distributed locations
- Priority: Critical

**BR-003: Multi-Role Authentication System**
- Implement JWT-based authentication for three user roles: Manufacturers, Distributors, and Pharmacists
- Support role-based access control (RBAC)
- Enable secure API access for mobile and web clients
- Priority: Critical

**BR-004: Blockchain Integration**
- Design smart contract interface for batch token minting
- Ensure immutable record of batch creation and recalls
- Optimize gas fees through Merkle root storage
- Priority: High

**BR-005: Anti-Counterfeiting Protection**
- Prevent QR code cloning and replay attacks
- Detect geographic anomalies in scan patterns
- Enable automatic fraud detection and response
- Priority: Critical

## 3. Functional Requirements

### 3.1 User Management

**FR-001: User Registration**
- System shall allow registration of three user types: Manufacturer, Distributor, Pharmacist
- Each manufacturer shall receive a unique public/private key pair
- System shall validate user credentials during registration
- System shall store passwords using bcrypt hashing (minimum cost factor: 12)

**FR-002: User Authentication**
- System shall authenticate users via username/password
- System shall generate JWT tokens with 24-hour expiration
- System shall support refresh tokens with 7-day expiration
- System shall invalidate tokens on logout

**FR-003: Role-Based Access Control**
- Manufacturers shall create batches, upload lab reports, and initiate recalls
- Distributors shall transfer ownership and scan units
- Pharmacists shall verify units and view batch details
- Regulators shall view all data and trigger emergency recalls

### 3.2 Batch Management

**FR-004: Batch Creation**
- System shall allow manufacturers to create batches with: batch number, medicine name, manufacturing date, expiry date
- System shall require lab report upload (PDF format, max 10MB)
- System shall calculate SHA-256 hash of lab report
- System shall store lab report in AWS S3
- System shall generate digital signature for batch using manufacturer's private key

**FR-005: Multi-Signature Approval**
- System shall require approval from at least 2 different roles before batch activation
- Required approvers: Production Head and Quality Checker
- Each approval shall include digital signature
- System shall only mint blockchain token after all approvals received

**FR-006: Batch Status Management**
- System shall support batch statuses: PROCESSING, PENDING_APPROVAL, ACTIVE, QUARANTINE, RECALLED, RECALLED_AUTO
- Manufacturers shall be able to quarantine their own batches
- Regulators shall be able to recall any batch
- System shall automatically recall batches flagged by AI Sentinel

**FR-007: Expiry Management**
- System shall automatically flag batches 30 days before expiry
- System shall send alerts to distributors holding near-expiry units
- System shall prevent distribution of expired batches
- System shall track expiry_alert_sent status

### 3.3 Unit Item Management

**FR-008: Bulk Unit Generation**
- System shall generate up to 1,000,000 unique serial numbers per batch
- System shall use message queue (RabbitMQ) for asynchronous processing
- System shall provide real-time progress updates via job status API
- System shall complete generation of 100,000 units within 5 minutes

**FR-009: Unit Serialization**
- Each unit shall have unique serial number format: {BATCH_ID}-{SEQUENCE}-{CHECKSUM}
- System shall support hierarchical packaging: Tablet → Strip → Box → Carton
- System shall maintain parent-child relationships via parent_unit_id
- System shall enable cascade operations on parent units

**FR-010: QR Code Generation**
- System shall generate encrypted QR payload containing: serial number, batch ID, timestamp, manufacturer signature, HMAC token
- System shall set max_scan_limit = 5 for each unit
- System shall set is_active = true on creation
- System shall store QR payload in database

**FR-011: QR Code Security**
- System shall prevent replay attacks via scan count tracking
- System shall warn users when scan_count > 3
- System shall flag units when scan_count > max_scan_limit
- System shall include HMAC token to prevent payload tampering

### 3.4 Verification and Scanning

**FR-012: QR Code Verification**
- System shall decrypt and validate QR payload
- System shall verify manufacturer's digital signature
- System shall check HMAC token integrity
- System shall verify unit is_active status
- System shall check batch status (not recalled/expired)

**FR-013: Scan Logging**
- System shall log every scan with: timestamp, location (lat/lng), IP address, device fingerprint, user ID
- System shall increment scan_count on each verification
- System shall calculate anomaly score for each scan
- System shall store scan result (VALID, INVALID, SUSPICIOUS, FLAGGED)

**FR-014: Geographic Anomaly Detection**
- System shall detect impossible travel (speed > 200 km/h between scans)
- System shall detect simultaneous scans (< 5 minutes apart, > 50 km distance)
- System shall track IP address and device fingerprint changes
- System shall auto-flag units with critical anomalies

**FR-015: Automatic Kill-Switch**
- System shall automatically set is_active = false for units with impossible travel
- System shall automatically set status = RECALLED_AUTO
- System shall generate fraud alert for regulators
- System shall log auto-triggered recall events

### 3.5 Ownership Transfer

**FR-016: Transfer Initiation**
- System shall allow current owner to initiate transfer to another user
- System shall create ownership_log entry with: from_user, to_user, timestamp, location
- System shall update unit's current_owner_id
- System shall support transfer types: MANUFACTURE_TO_DISTRIBUTOR, DISTRIBUTOR_TO_PHARMACY, PHARMACY_TO_PATIENT

**FR-017: Transfer History**
- System shall maintain complete ownership timeline for each unit
- System shall display transfer history in chronological order
- System shall show location and timestamp for each transfer
- System shall enable traceability UI (stepper/timeline view)

### 3.6 Blockchain Integration

**FR-018: Batch Token Minting**
- System shall mint blockchain token only after multi-signature approval
- System shall store on blockchain: batch number, medicine hash, dates, manufacturer address, lab report hash, Merkle root
- System shall NOT store individual units on blockchain (gas optimization)
- System shall store transaction ID in database

**FR-019: Merkle Tree Verification**
- System shall calculate Merkle root from all unit serial numbers
- System shall store Merkle root on blockchain
- System shall enable verification of any unit against Merkle root
- System shall detect database tampering via Merkle root mismatch

**FR-020: Recall Events**
- System shall emit blockchain event for all recalls
- System shall store: batch number, initiator address, reason, timestamp, auto_triggered flag
- System shall make recall events publicly queryable

### 3.7 Lab Report Management

**FR-021: Lab Report Upload**
- System shall accept PDF files up to 10MB
- System shall calculate SHA-256 hash before upload
- System shall upload to AWS S3 with encryption (SSE-S3)
- System shall store S3 key and hash in database

**FR-022: Lab Report Integrity**
- System shall verify file integrity on retrieval
- System shall compare current hash with stored hash
- System shall flag tampering if hash mismatch detected
- System shall generate alert for regulators on tampering

**FR-023: Lab Report Access**
- System shall generate pre-signed URLs for secure access
- URLs shall expire after 15 minutes
- Only authorized users shall access lab reports
- System shall log all lab report access attempts

### 3.8 Recall Management

**FR-024: Manual Recall**
- Manufacturers shall recall their own batches with reason
- Regulators shall recall any batch (emergency kill-switch)
- System shall immediately set batch status = RECALLED
- System shall cascade recall to all child units

**FR-025: Automatic Recall**
- AI Sentinel shall auto-recall units with critical anomalies
- System shall set status = RECALLED_AUTO
- System shall generate detailed fraud report
- System shall notify all stakeholders

**FR-026: Recall Notification**
- System shall send real-time alerts to all users holding recalled units
- Pharmacists shall see "DO NOT SELL" warning on scan
- Distributors shall see recalled units in inventory
- System shall track recall acknowledgment

### 3.9 AI Sentinel

**FR-027: Anomaly Detection**
- System shall analyze scan patterns for each unit
- System shall calculate anomaly score (0.0 to 1.0)
- System shall flag scans with score > 0.7
- System shall auto-recall units with score = 1.0

**FR-028: Device Fingerprinting**
- System shall generate unique device fingerprint from: User-Agent, screen resolution, timezone, language, plugins
- System shall track device changes across scans
- System shall flag multiple device changes as suspicious
- System shall combine with geographic data for fraud detection

**FR-029: Predictive Risk Scoring**
- System shall calculate risk score for batches during creation
- System shall analyze: raw material sources, temperature logs, lab result patterns
- System shall flag high-risk batches before minting
- System shall require additional approval for high-risk batches

### 3.10 Job Processing

**FR-030: Asynchronous Job Management**
- System shall use RabbitMQ for background job processing
- System shall support job types: UNIT_GENERATION, QR_GENERATION, BLOCKCHAIN_MINT
- System shall track job status: QUEUED, PROCESSING, COMPLETED, FAILED
- System shall provide job progress percentage

**FR-031: Job Status API**
- Users shall query job status via job ID
- System shall return: status, progress, total items, processed items, error message
- System shall support job cancellation for queued jobs
- System shall enable retry for failed jobs

## 4. Non-Functional Requirements

### 4.1 Performance

**NFR-001: API Response Time**
- 95th percentile response time shall be < 200ms for read operations
- 95th percentile response time shall be < 500ms for write operations
- Batch creation API shall return immediately (< 100ms) with job ID

**NFR-002: Bulk Processing**
- System shall generate 100,000 units in < 5 minutes
- System shall support concurrent processing of multiple batches
- System shall handle 1,000 concurrent API requests

**NFR-003: Database Performance**
- Database queries shall use appropriate indexes
- Serial number lookup shall complete in < 50ms
- Batch listing shall support pagination (50 items per page)

### 4.2 Scalability

**NFR-004: Horizontal Scaling**
- Application shall be stateless to enable horizontal scaling
- System shall support deployment of multiple app instances
- Message queue shall distribute jobs across workers

**NFR-005: Data Volume**
- System shall support 10,000+ batches
- System shall support 1,000,000,000+ unit items
- System shall support 10,000,000+ scan logs

### 4.3 Security

**NFR-006: Authentication Security**
- JWT tokens shall use HS512 algorithm with 512-bit secret
- Passwords shall be hashed with bcrypt (cost factor: 12)
- API shall enforce rate limiting: 100 requests/minute per user

**NFR-007: Data Encryption**
- All API communication shall use TLS 1.3
- S3 bucket shall use server-side encryption (SSE-S3 or SSE-KMS)
- Sensitive data at rest shall use AES-256 encryption

**NFR-008: Key Management**
- Private keys shall be stored in AWS Secrets Manager or HashiCorp Vault
- Keys shall be rotated every 90 days
- Separate keys for dev, staging, production environments

**NFR-009: Access Control**
- System shall implement principle of least privilege
- Regulator endpoints shall use IP whitelisting
- All privileged operations shall require multi-factor authentication

### 4.4 Reliability

**NFR-010: Availability**
- System shall maintain 99.9% uptime (< 8.76 hours downtime/year)
- Database shall have automated backups every 6 hours
- System shall support zero-downtime deployments

**NFR-011: Data Integrity**
- Database transactions shall be ACID-compliant
- System shall use optimistic locking for concurrent updates
- Merkle root shall detect any database tampering

**NFR-012: Fault Tolerance**
- Failed jobs shall be automatically retried (max 3 attempts)
- System shall gracefully handle blockchain network failures
- S3 upload failures shall be retried with exponential backoff

### 4.5 Monitoring and Observability

**NFR-013: Logging**
- All API requests shall be logged with request ID
- All errors shall be logged with stack traces
- Logs shall be structured (JSON format)
- Logs shall be centralized (ELK stack or CloudWatch)

**NFR-014: Metrics**
- System shall track: API response times, database query times, job processing times, blockchain transaction success rate
- Metrics shall be collected every 60 seconds
- Metrics shall be visualized in dashboards (Grafana or CloudWatch)

**NFR-015: Alerts**
- System shall alert on: failed blockchain transactions, anomaly score > 0.8, geographic fraud detection, system resource > 80%
- Alerts shall be sent via email and SMS
- Critical alerts shall page on-call engineer

### 4.6 Compliance

**NFR-016: Audit Trail**
- All batch operations shall be logged in audit table
- All approval actions shall be logged with digital signatures
- All recall events shall be logged with reason and initiator
- Audit logs shall be immutable and tamper-proof

**NFR-017: Data Retention**
- Batch data shall be retained for 10 years
- Scan logs shall be retained for 5 years
- Audit logs shall be retained indefinitely
- Deleted data shall be archived, not permanently deleted

**NFR-018: Regulatory Compliance**
- System shall comply with FDA 21 CFR Part 11 (electronic records)
- System shall comply with GDPR for personal data
- System shall support regulatory audits with exportable reports

### 4.7 Usability

**NFR-019: API Documentation**
- All APIs shall be documented with OpenAPI/Swagger
- Documentation shall include request/response examples
- Documentation shall be versioned (v1, v2, etc.)

**NFR-020: Error Messages**
- Error responses shall include: error code, human-readable message, timestamp, request ID
- Error messages shall not expose sensitive information
- Error messages shall be localized (English, Hindi)

### 4.8 Maintainability

**NFR-021: Code Quality**
- Code shall follow Java coding standards (Google Java Style Guide)
- Unit test coverage shall be > 80%
- Integration test coverage shall be > 60%
- Critical algorithms shall have 100% test coverage

**NFR-022: Documentation**
- All services shall have JavaDoc comments
- Architecture decisions shall be documented (ADRs)
- Deployment procedures shall be documented
- Runbooks shall be created for common issues

## 5. Data Requirements

### 5.1 Data Migration

**DR-001: SQLite to PostgreSQL Migration**
- All existing batch data shall be migrated
- All existing unit data shall be migrated
- Data integrity shall be verified post-migration
- Rollback plan shall be prepared

**DR-002: File to S3 Migration**
- All existing lab reports shall be uploaded to S3
- File hashes shall be calculated and stored
- Local files shall be archived after successful migration

### 5.2 Data Validation

**DR-003: Input Validation**
- Batch numbers shall match pattern: [A-Z0-9]{8,16}
- Dates shall be validated (manufacturing < expiry)
- File uploads shall be validated (type, size, content)
- Serial numbers shall be unique across system

**DR-004: Business Rule Validation**
- Expired batches shall not be activated
- Recalled units shall not be transferable
- Inactive units shall not be scannable
- Quarantined batches shall not be distributed

## 6. Integration Requirements

### 6.1 AWS Integration

**IR-001: S3 Integration**
- System shall use AWS SDK for Java v2
- System shall handle S3 exceptions gracefully
- System shall support multi-region S3 buckets
- System shall use S3 lifecycle policies for archival

**IR-002: Secrets Manager Integration**
- System shall retrieve secrets at startup
- System shall cache secrets for 1 hour
- System shall rotate secrets automatically
- System shall handle secret rotation without downtime

### 6.2 Blockchain Integration

**IR-003: Smart Contract Interface**
- System shall use Web3j library for Ethereum interaction
- System shall support both Ethereum mainnet and testnets
- System shall handle gas price fluctuations
- System shall retry failed transactions (max 3 attempts)

**IR-004: Transaction Monitoring**
- System shall monitor transaction status
- System shall wait for 12 confirmations before marking complete
- System shall handle transaction failures gracefully
- System shall log all blockchain interactions

### 6.3 Message Queue Integration

**IR-005: RabbitMQ Integration**
- System shall use Spring AMQP for RabbitMQ
- System shall declare queues on startup
- System shall handle connection failures with retry
- System shall use dead-letter queues for failed messages

## 7. Testing Requirements

### 7.1 Unit Testing

**TR-001: Service Layer Testing**
- All service methods shall have unit tests
- Tests shall use mocking for dependencies
- Tests shall cover happy path and error cases
- Tests shall verify security constraints

### 7.2 Integration Testing

**TR-002: API Testing**
- All endpoints shall have integration tests
- Tests shall verify authentication and authorization
- Tests shall verify request/response formats
- Tests shall verify error handling

**TR-003: Database Testing**
- Tests shall verify CRUD operations
- Tests shall verify transaction rollback
- Tests shall verify constraint enforcement
- Tests shall use test containers for PostgreSQL

### 7.3 Performance Testing

**TR-004: Load Testing**
- System shall be tested with 1,000 concurrent users
- Bulk operations shall be tested with 100,000+ items
- Database queries shall be profiled for optimization
- API response times shall be measured under load

### 7.4 Security Testing

**TR-005: Penetration Testing**
- System shall undergo penetration testing before production
- Common vulnerabilities shall be tested (OWASP Top 10)
- Authentication and authorization shall be tested
- Cryptographic implementations shall be reviewed

## 8. Deployment Requirements

### 8.1 Environment Setup

**DEP-001: Infrastructure**
- System shall be deployed on AWS EC2 or ECS
- PostgreSQL shall be deployed on AWS RDS
- RabbitMQ shall be deployed on AWS MQ or EC2
- Redis shall be deployed on AWS ElastiCache

**DEP-002: Configuration Management**
- Environment-specific configs shall be externalized
- Secrets shall never be committed to version control
- Configuration shall be validated on startup
- Configuration changes shall not require code changes

### 8.2 Deployment Strategy

**DEP-003: Blue-Green Deployment**
- New version shall be deployed alongside old version
- Traffic shall be gradually shifted to new version
- Rollback shall be possible within 5 minutes
- Database migrations shall be backward-compatible

**DEP-004: Monitoring**
- Health check endpoint shall be available at /actuator/health
- Readiness probe shall verify database connectivity
- Liveness probe shall verify application responsiveness
- Metrics shall be exposed at /actuator/metrics

## 9. Success Criteria

### 9.1 Functional Success

- All user roles can authenticate and access appropriate features
- Batches can be created, approved, and minted on blockchain
- 100,000 units can be generated in < 5 minutes
- QR codes can be verified with < 200ms response time
- Geographic anomalies are detected and auto-recalled
- Ownership transfers are tracked with complete history

### 9.2 Non-Functional Success

- System maintains 99.9% uptime for 30 days
- API response times meet SLA (95th percentile < 200ms)
- Zero security vulnerabilities in production
- All data successfully migrated from SQLite to PostgreSQL
- Lab reports successfully migrated to AWS S3
- Blockchain integration operational with < 1% transaction failure rate

### 9.3 Business Success

- System supports 100+ manufacturers
- System tracks 1,000,000+ pharmaceutical units
- Zero counterfeit drugs detected in system
- Recall notifications delivered within 1 minute
- Regulatory audit completed successfully
- User satisfaction score > 4.5/5.0

---

**Document Version**: 1.0  
**Last Updated**: 2026-03-12  
**Status**: Draft  
**Approved By**: Pending
