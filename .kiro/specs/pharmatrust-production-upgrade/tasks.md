# Implementation Plan: PharmaTrust Production Upgrade

## Overview

This implementation plan transforms PharmaTrust-AI from a prototype to a production-ready pharmaceutical supply chain security platform. The implementation focuses on enterprise-grade infrastructure, robust security, and scalable architecture using Java/Spring Boot.

Key technical areas:
- PostgreSQL database migration with optimized schema
- AWS S3 integration for lab report storage
- JWT authentication with granular RBAC
- Blockchain integration (Merkle root only for gas optimization)
- Protobuf-based QR compression with offline verification
- RabbitMQ message queue for async batch processing
- AI Sentinel with geographic anomaly detection and auto kill-switch
- Idempotent batch processing with crash-safety
- Ownership transfer tracking system

## Tasks

- [x] 1. Set up project infrastructure and dependencies
  - Update pom.xml with required dependencies: PostgreSQL driver, AWS SDK v2, Spring Security, JWT library, Web3j, RabbitMQ, Protobuf, Redis
  - Configure application.properties for PostgreSQL, AWS S3, JWT, RabbitMQ, and blockchain
  - Set up Docker Compose with PostgreSQL, RabbitMQ, and Redis services
  - Create database migration scripts using Flyway or Liquibase
  - _Requirements: BR-001, BR-002, NFR-010, DEP-001_

- [~] 2. Implement PostgreSQL database schema and repositories
  - [x] 2.1 Create JPA entity models for Users, Batches, UnitItems, ScanLogs, BatchApprovals, RecallEvents, OwnershipLogs, BatchJobs
    - Define all entity relationships (one-to-many, many-to-one)
    - Add database indexes for performance optimization
    - Implement UUID primary keys for all entities
    - _Requirements: FR-001, FR-004, FR-008, NFR-003, DR-003_

  - [x] 2.2 Create Spring Data JPA repositories for all entities
    - Implement custom query methods for complex lookups
    - Add idempotent save methods using ON CONFLICT DO NOTHING
    - Create repository methods for Merkle tree calculation
    - _Requirements: FR-008, FR-019, NFR-011_

  - [ ]* 2.3 Write unit tests for repository layer
    - Test CRUD operations for all entities
    - Test custom query methods
    - Test constraint enforcement and cascading operations
    - Use Testcontainers for PostgreSQL integration tests
    - _Requirements: TR-003_

- [x] 3. Implement cryptography service for digital signatures and hashing
  - [x] 3.1 Create CryptographyService with RSA/ECDSA key generation
    - Implement generateRSAKeyPair() and generateECDSAKeyPair() methods
    - Implement SHA-256 hashing for file integrity
    - Implement digital signature generation and verification
    - Implement HMAC generation for QR payload integrity
    - _Requirements: FR-004, FR-010, FR-011, NFR-007_

  - [x] 3.2 Implement key management with AWS Secrets Manager integration
    - Store and retrieve manufacturer private keys securely
    - Implement key rotation mechanism (90-day policy)
    - Cache keys with 1-hour TTL
    - _Requirements: NFR-008, IR-002_

  - [ ]* 3.3 Write unit tests for cryptography service
    - Test signature generation and verification
    - Test hash calculation consistency
    - Test HMAC token generation and validation
    - Verify key pair generation produces valid keys
    - _Requirements: TR-001, NFR-021_

- [x] 4. Implement JWT authentication and authorization system
  - [x] 4.1 Create AuthenticationService with login/logout functionality
    - Implement user registration with bcrypt password hashing (cost factor 12)
    - Implement JWT token generation with HS512 algorithm
    - Implement token validation and refresh mechanism
    - Store JWT secret in environment variables
    - _Requirements: FR-001, FR-002, NFR-006_

  - [x] 4.2 Create JWT authentication filter for Spring Security
    - Implement JwtAuthenticationFilter to intercept requests
    - Extract and validate JWT from Authorization header
    - Set SecurityContext with authenticated user details
    - Handle token expiration and invalid tokens
    - _Requirements: FR-002, NFR-006_

  - [x] 4.3 Implement granular RBAC with role-based access control
    - Define roles: MANUFACTURER, DISTRIBUTOR, PHARMACIST, REGULATOR
    - Implement method-level security annotations (@PreAuthorize)
    - Create RegulatorAccessControl for privacy-protected endpoints
    - Ensure regulators can only access alerts/recalls, not business metrics
    - _Requirements: FR-003, NFR-009, BR-003_

  - [ ]* 4.4 Write integration tests for authentication endpoints
    - Test user registration and login flows
    - Test JWT token generation and validation
    - Test role-based access control enforcement
    - Test unauthorized access attempts
    - _Requirements: TR-002_

- [x] 5. Checkpoint - Ensure authentication and database layers are working
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement AWS S3 storage service for lab reports
  - [x] 6.1 Create StorageService with S3 client integration
    - Implement uploadLabReport() with multipart file handling
    - Calculate SHA-256 hash before upload
    - Store files with encryption (SSE-S3)
    - Implement generatePresignedUrl() with 15-minute expiration
    - _Requirements: FR-021, FR-023, BR-002, NFR-007_

  - [x] 6.2 Implement file integrity verification
    - Verify file hash on retrieval matches stored hash
    - Generate alerts on hash mismatch (tampering detection)
    - Implement deleteFile() for cleanup operations
    - _Requirements: FR-022, NFR-011_

  - [ ]* 6.3 Write integration tests for S3 storage service
    - Test file upload and download operations
    - Test pre-signed URL generation
    - Test file integrity verification
    - Use LocalStack or S3Mock for testing
    - _Requirements: TR-002_

- [x] 7. Implement Protobuf-based QR code service with offline verification
  - [x] 7.1 Define Protobuf schema for compact QR payload
    - Create qr_payload.proto with fields: serial_number (16 bytes), batch_id_hash (8 bytes), timestamp (4 bytes), signature (64 bytes)
    - Generate Java classes from Protobuf schema
    - Target payload size: ~100 bytes (vs 300+ bytes JSON)
    - _Requirements: FR-010, FR-011_

  - [x] 7.2 Create QRCodeService with compact payload generation
    - Implement generateQRPayload() using Protobuf serialization
    - Sign payload with ECDSA for compact 64-byte signature
    - Base64 encode for URL safety
    - Generate QR code image using ZXing library
    - _Requirements: FR-010, FR-011_

  - [x] 7.3 Implement offline verification support
    - Implement verifyQROffline() using cached manufacturer public keys
    - Verify ECDSA signature without database lookup
    - Return "VALID_OFFLINE" status with sync pending indicator
    - Cache public keys in Redis with 24-hour TTL
    - _Requirements: FR-012_

  - [x] 7.4 Implement online verification with scan logging
    - Implement verifyQROnline() with full database validation
    - Verify signature, check is_active status, check batch status
    - Increment scan_count and check against max_scan_limit
    - Log scan with timestamp, location, IP, device fingerprint
    - _Requirements: FR-012, FR-013_

  - [ ]* 7.5 Write unit tests for QR code service
    - Test Protobuf serialization and deserialization
    - Test QR payload generation and parsing
    - Test offline signature verification
    - Test scan count enforcement
    - _Requirements: TR-001_

- [x] 8. Implement AI Sentinel service for fraud detection
  - [~] 8.1 Create AISentinelService with geographic anomaly detection
    - Implement detectGeographicAnomaly() for impossible travel detection
    - Calculate distance between consecutive scans using Haversine formula
    - Detect speed > 200 km/h as impossible travel
    - Detect simultaneous scans (< 5 min apart, > 50 km distance)
    - _Requirements: FR-014, FR-027_

  - [~] 8.2 Implement automatic kill-switch mechanism
    - Auto-set is_active = false for units with critical anomalies
    - Set status = RECALLED_AUTO
    - Generate fraud alert for regulators
    - Log auto-triggered recall events
    - _Requirements: FR-015, FR-025_

  - [~] 8.3 Implement device fingerprinting
    - Generate unique fingerprint from User-Agent, screen resolution, timezone, language
    - Track device changes across scans
    - Flag multiple device changes as suspicious
    - Combine with geographic data for fraud scoring
    - _Requirements: FR-028_

  - [~] 8.4 Implement anomaly score calculation
    - Calculate score (0.0 to 1.0) based on multiple factors
    - Flag scans with score > 0.7 as suspicious
    - Auto-recall units with score = 1.0
    - Store anomaly_score in scan_logs table
    - _Requirements: FR-027_

  - [ ]* 8.5 Write unit tests for AI Sentinel service
    - Test impossible travel detection with various scenarios
    - Test device fingerprint generation consistency
    - Test anomaly score calculation
    - Test auto kill-switch triggering
    - _Requirements: TR-001_

- [x] 9. Checkpoint - Ensure security and fraud detection systems are working
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement RabbitMQ message queue for async batch processing
  - [x] 10.1 Configure RabbitMQ with Spring AMQP
    - Define queues: unit-generation-queue, qr-generation-queue, blockchain-mint-queue
    - Configure dead-letter queues for failed messages
    - Set up connection factory with retry mechanism
    - Declare queues on application startup
    - _Requirements: FR-030, IR-005, BR-004_

  - [x] 10.2 Create MessageQueueService for job management
    - Implement enqueueJob() to publish messages to RabbitMQ
    - Create BatchJob entity to track job status
    - Implement getJobStatus() API endpoint
    - Support job types: UNIT_GENERATION, QR_GENERATION, BLOCKCHAIN_MINT
    - _Requirements: FR-030, FR-031_

  - [x] 10.3 Implement job status tracking and retry mechanism
    - Track job status: QUEUED, PROCESSING, COMPLETED, FAILED
    - Implement automatic retry for failed jobs (max 3 attempts)
    - Store error messages for debugging
    - Provide real-time progress updates
    - _Requirements: FR-031, NFR-012_

  - [ ]* 10.4 Write integration tests for message queue service
    - Test job enqueueing and processing
    - Test job status tracking
    - Test retry mechanism for failed jobs
    - Use embedded RabbitMQ for testing
    - _Requirements: TR-002_

- [x] 11. Implement idempotent bulk unit generation with crash-safety
  - [x] 11.1 Create BatchService with async batch creation
    - Implement createBatch() that returns immediately with job ID
    - Create batch record with status = PROCESSING
    - Enqueue unit generation job to RabbitMQ
    - Store lab report in S3 and calculate hash
    - _Requirements: FR-004, FR-008, NFR-001_

  - [x] 11.2 Implement background worker for unit generation
    - Create @RabbitListener for unit-generation-queue
    - Generate deterministic serial numbers: {BATCH_ID}-{INDEX}-{CHECKSUM}
    - Use batch inserts (1000 units per batch) for performance
    - Implement idempotent inserts using ON CONFLICT DO NOTHING
    - Update job progress in real-time
    - _Requirements: FR-008, FR-009, NFR-002_

  - [x] 11.3 Implement Merkle tree calculation and storage
    - Calculate Merkle root from all unit serial numbers
    - Store merkle_root in batches table
    - Implement verifyUnitAgainstMerkleRoot() for integrity checks
    - _Requirements: FR-019, NFR-011_

  - [ ]* 11.4 Write integration tests for batch service
    - Test batch creation and async processing
    - Test deterministic serial number generation
    - Test idempotent inserts (retry safety)
    - Test Merkle root calculation
    - Test job progress tracking
    - _Requirements: TR-002, NFR-021_

- [x] 12. Implement multi-signature approval workflow
  - [x] 12.1 Create BatchApprovalService for approval management
    - Implement approveBatch() with digital signature verification
    - Require approvals from PRODUCTION_HEAD and QUALITY_CHECKER roles
    - Store approval with digital signature in batch_approvals table
    - Check if all required approvals received
    - _Requirements: FR-005_

  - [x] 12.2 Implement batch activation logic
    - Activate batch only after all approvals received
    - Change batch status from PENDING_APPROVAL to ACTIVE
    - Trigger blockchain minting after activation
    - _Requirements: FR-005, FR-018_

  - [ ]* 12.3 Write unit tests for approval workflow
    - Test approval requirement enforcement
    - Test digital signature verification
    - Test batch activation after all approvals
    - Test rejection of insufficient approvals
    - _Requirements: TR-001_

- [x] 13. Implement blockchain integration with gas optimization
  - [x] 13.1 Create BlockchainService with Web3j integration
    - Configure Web3j client for Ethereum network
    - Load smart contract ABI and address from configuration
    - Implement connection retry mechanism
    - Handle gas price fluctuations
    - _Requirements: BR-004, IR-003, IR-004_

  - [x] 13.2 Implement batch token minting (Merkle root only)
    - Implement mintBatchToken() to store only Merkle root on-chain
    - Do NOT store individual units (gas optimization)
    - Store: batch_number, medicine_hash, dates, manufacturer_address, lab_report_hash, merkle_root, total_units
    - Wait for 12 confirmations before marking complete
    - Store transaction ID in database
    - _Requirements: FR-018, FR-019_

  - [x] 13.3 Implement recall event emission
    - Implement emitRecallEvent() to publish recall on blockchain
    - Store: batch_number, initiator_address, reason, timestamp, auto_triggered flag
    - Make recall events publicly queryable
    - _Requirements: FR-020_

  - [x] 13.4 Implement transaction monitoring
    - Monitor transaction status asynchronously
    - Handle transaction failures with retry (max 3 attempts)
    - Log all blockchain interactions
    - Generate alerts on transaction failures
    - _Requirements: IR-004, NFR-012_

  - [ ]* 13.5 Write integration tests for blockchain service
    - Test batch token minting
    - Test recall event emission
    - Test transaction monitoring
    - Use Ganache or mock blockchain for testing
    - _Requirements: TR-002_

- [x] 14. Checkpoint - Ensure async processing and blockchain integration are working
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement batch management endpoints
  - [x] 15.1 Create BatchController with REST endpoints
    - POST /api/v1/batches - Create new batch (returns job ID immediately)
    - GET /api/v1/batches - List batches with pagination and filtering
    - GET /api/v1/batches/{id} - Get batch details with units
    - POST /api/v1/batches/{id}/approve - Approve batch with digital signature
    - POST /api/v1/batches/{id}/quarantine - Quarantine batch
    - POST /api/v1/batches/{id}/recall - Recall batch with reason
    - _Requirements: FR-004, FR-005, FR-006, FR-024_

  - [x] 15.2 Implement batch status management
    - Support statuses: PROCESSING, PENDING_APPROVAL, ACTIVE, QUARANTINE, RECALLED, RECALLED_AUTO
    - Implement quarantineBatch() for manufacturers
    - Implement recallBatch() for manufacturers and regulators
    - Implement cascade recall to all child units
    - _Requirements: FR-006, FR-024, FR-025_

  - [x] 15.3 Implement expiry management
    - Calculate expiry_warning_date (30 days before expiry)
    - Auto-flag batches approaching expiry
    - Send alerts to distributors holding near-expiry units
    - Prevent distribution of expired batches
    - _Requirements: FR-007_

  - [ ]* 15.4 Write integration tests for batch endpoints
    - Test batch creation and approval workflow
    - Test batch status transitions
    - Test quarantine and recall operations
    - Test expiry management
    - _Requirements: TR-002_

- [x] 16. Implement QR verification and scanning endpoints
  - [x] 16.1 Create VerifyController with verification endpoints
    - POST /api/v1/verify/scan - Verify QR code (online mode)
    - POST /api/v1/verify/scan/offline - Verify QR code (offline mode)
    - GET /api/v1/verify/unit/{serial} - Get unit details
    - GET /api/v1/verify/history/{serial} - Get scan history
    - _Requirements: FR-012, FR-013_

  - [x] 16.2 Implement scan logging with anomaly detection
    - Log every scan with timestamp, location, IP, device fingerprint
    - Increment scan_count on each verification
    - Call AI Sentinel for anomaly detection
    - Return scan result: VALID, INVALID, SUSPICIOUS, FLAGGED
    - _Requirements: FR-013, FR-014_

  - [x] 16.3 Implement scan count enforcement
    - Warn users when scan_count > 3
    - Flag units when scan_count > max_scan_limit (default: 5)
    - Auto-trigger investigation for flagged units
    - _Requirements: FR-011_

  - [ ]* 16.4 Write integration tests for verification endpoints
    - Test online and offline verification
    - Test scan logging and count enforcement
    - Test anomaly detection integration
    - Test invalid QR code handling
    - _Requirements: TR-002_

- [x] 17. Implement ownership transfer system
  - [x] 17.1 Create TransferController with transfer endpoints
    - POST /api/v1/transfer/initiate - Initiate unit transfer
    - POST /api/v1/transfer/accept - Accept transfer
    - GET /api/v1/transfer/history/{serial} - Get ownership timeline
    - GET /api/v1/transfer/pending - Get pending transfers
    - _Requirements: FR-016, FR-017_

  - [x] 17.2 Implement ownership tracking
    - Create ownership_logs entry for each transfer
    - Update unit's current_owner_id
    - Support transfer types: MANUFACTURE_TO_DISTRIBUTOR, DISTRIBUTOR_TO_PHARMACY, PHARMACY_TO_PATIENT
    - Store location and timestamp for each transfer
    - _Requirements: FR-016, FR-017_

  - [x] 17.3 Implement parent-child cascade operations
    - Support hierarchical packaging: Tablet → Strip → Box → Carton
    - Maintain parent-child relationships via parent_unit_id
    - Cascade transfers to all child units
    - Cascade recalls to all child units
    - _Requirements: FR-009, FR-025_

  - [ ]* 17.4 Write integration tests for transfer endpoints
    - Test transfer initiation and acceptance
    - Test ownership history tracking
    - Test cascade operations
    - Test transfer validation rules
    - _Requirements: TR-002_

- [x] 18. Implement regulator endpoints with granular RBAC
  - [x] 18.1 Create RegulatorController with privacy-protected endpoints
    - GET /api/v1/regulator/alerts - View fraud alerts only
    - GET /api/v1/regulator/recalls - View recall events only
    - POST /api/v1/regulator/kill-switch - Emergency recall
    - GET /api/v1/regulator/flagged-batches - View flagged batches only
    - GET /api/v1/regulator/audit-logs - View audit trail (no business metrics)
    - _Requirements: FR-003, NFR-009_

  - [x] 18.2 Implement privacy protection for regulators
    - Ensure regulators CANNOT see: production volumes, supply chain routes, business metrics
    - Ensure regulators CAN see: anomalies, recalls, fraud alerts, flagged items
    - Log all regulator access for manufacturer transparency
    - Require IP whitelisting for regulator endpoints
    - _Requirements: NFR-009_

  - [ ]* 18.3 Write integration tests for regulator endpoints
    - Test access control enforcement
    - Test privacy protection (no business data leakage)
    - Test emergency kill-switch functionality
    - Test audit logging of regulator actions
    - _Requirements: TR-002_

- [x] 19. Implement job status and monitoring endpoints
  - [x] 19.1 Create JobController for job management
    - GET /api/v1/jobs/{jobId} - Get job status
    - GET /api/v1/jobs/batch/{batchId} - Get batch jobs
    - POST /api/v1/jobs/{jobId}/cancel - Cancel running job
    - POST /api/v1/jobs/{jobId}/retry - Retry failed job
    - _Requirements: FR-031_

  - [x] 19.2 Implement real-time progress tracking
    - Return job status: QUEUED, PROCESSING, COMPLETED, FAILED
    - Return progress percentage: processed_items / total_items
    - Return error messages for failed jobs
    - Support WebSocket for real-time updates (optional)
    - _Requirements: FR-031_

  - [ ]* 19.3 Write integration tests for job endpoints
    - Test job status retrieval
    - Test job cancellation
    - Test job retry mechanism
    - _Requirements: TR-002_

- [x] 20. Checkpoint - Ensure all API endpoints are working
  - Ensure all tests pass, ask the user if questions arise.

- [x] 21. Implement data migration from SQLite to PostgreSQL
  - [x] 21.1 Create migration scripts for existing data
    - Export existing batch data from SQLite
    - Transform schema to PostgreSQL format
    - Migrate batch and unit data with integrity checks
    - Verify data integrity post-migration
    - _Requirements: DR-001_

  - [x] 21.2 Migrate lab reports to AWS S3
    - Upload all existing lab reports to S3
    - Calculate and store SHA-256 hashes
    - Update database with S3 keys
    - Archive local files after successful migration
    - _Requirements: DR-002_

  - [ ]* 21.3 Write validation tests for data migration
    - Verify all records migrated successfully
    - Verify data integrity (counts, relationships)
    - Verify lab report accessibility from S3
    - _Requirements: DR-001, DR-002_

- [x] 22. Implement monitoring, logging, and alerting
  - [x] 22.1 Configure structured logging with Logback
    - Use JSON format for all logs
    - Include request ID in all log entries
    - Log all API requests with response times
    - Log all errors with stack traces
    - _Requirements: NFR-013_

  - [x] 22.2 Implement metrics collection with Micrometer
    - Track API response times
    - Track database query times
    - Track job processing times
    - Track blockchain transaction success rate
    - Expose metrics at /actuator/metrics
    - _Requirements: NFR-014_

  - [x] 22.3 Configure health checks and readiness probes
    - Implement /actuator/health endpoint
    - Verify database connectivity in readiness probe
    - Verify RabbitMQ connectivity in readiness probe
    - Implement liveness probe for application responsiveness
    - _Requirements: DEP-004_

  - [x] 22.4 Implement alerting for critical events
    - Alert on failed blockchain transactions
    - Alert on anomaly score > 0.8
    - Alert on geographic fraud detection
    - Alert on system resource > 80%
    - Configure email and SMS notifications
    - _Requirements: NFR-015_

- [x] 23. Implement API documentation with OpenAPI/Swagger
  - [x] 23.1 Configure Springdoc OpenAPI
    - Add Springdoc dependency to pom.xml
    - Configure OpenAPI metadata (title, version, description)
    - Add security scheme for JWT authentication
    - Generate API documentation at /swagger-ui.html
    - _Requirements: NFR-019_

  - [x] 23.2 Add API documentation annotations
    - Add @Operation annotations to all endpoints
    - Add @ApiResponse annotations for all response codes
    - Add request/response examples
    - Document authentication requirements
    - _Requirements: NFR-019_

- [x] 24. Implement security hardening
  - [x] 24.1 Configure rate limiting
    - Implement rate limiter using Bucket4j or Redis
    - Limit to 100 requests/minute per user
    - Return 429 Too Many Requests on limit exceeded
    - _Requirements: NFR-006_

  - [x] 24.2 Implement input validation
    - Validate batch numbers match pattern: [A-Z0-9]{8,16}
    - Validate dates (manufacturing < expiry)
    - Validate file uploads (type, size, content)
    - Validate serial number uniqueness
    - _Requirements: DR-003_

  - [x] 24.3 Configure CORS and security headers
    - Configure CORS for allowed origins
    - Add security headers: X-Frame-Options, X-Content-Type-Options, CSP
    - Enable HTTPS only (HSTS)
    - _Requirements: NFR-007_

  - [ ]* 24.4 Perform security testing
    - Test authentication and authorization
    - Test input validation and SQL injection prevention
    - Test CSRF protection
    - Test rate limiting
    - _Requirements: TR-005_

- [x] 25. Implement performance optimization
  - [x] 25.1 Add database indexes for performance
    - Create indexes on: batch_number, serial_number, batch_id, parent_unit_id, unit_id, scanned_at
    - Analyze query performance with EXPLAIN
    - Optimize slow queries
    - _Requirements: NFR-003_

  - [x] 25.2 Implement caching with Redis
    - Cache frequently accessed batch data (5-minute TTL)
    - Cache manufacturer public keys (24-hour TTL)
    - Implement cache invalidation on batch status change
    - _Requirements: NFR-003_

  - [x] 25.3 Optimize bulk operations
    - Use batch inserts for unit generation (1000 units per batch)
    - Use parallel processing for QR generation
    - Optimize Merkle tree calculation
    - _Requirements: NFR-002_

  - [ ]* 25.4 Perform load testing
    - Test with 1,000 concurrent users
    - Test bulk operations with 100,000+ items
    - Measure API response times under load
    - Verify performance meets SLA (95th percentile < 200ms)
    - _Requirements: TR-004, NFR-001_

- [x] 26. Final checkpoint - End-to-end testing and deployment preparation
  - [x] 26.1 Run full integration test suite
    - Verify all endpoints working correctly
    - Verify authentication and authorization
    - Verify async job processing
    - Verify blockchain integration
    - _Requirements: TR-002_

  - [x] 26.2 Prepare deployment configuration
    - Create environment-specific configuration files
    - Document environment variables
    - Create deployment scripts
    - Prepare rollback procedures
    - _Requirements: DEP-002, DEP-003_

  - [x] 26.3 Create deployment documentation
    - Document infrastructure setup
    - Document configuration management
    - Document monitoring and alerting setup
    - Create runbooks for common issues
    - _Requirements: NFR-022_

  - [x] 26.4 Final verification
    - Ensure all tests pass
    - Verify data migration completed successfully
    - Verify all security measures in place
    - Ask the user if questions arise before production deployment

## Notes

- Tasks marked with `*` are optional testing tasks and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Implementation uses Java/Spring Boot as specified in the design document
- Focus on idempotent operations and crash-safety for production reliability
- Prioritize security, performance, and scalability throughout implementation
