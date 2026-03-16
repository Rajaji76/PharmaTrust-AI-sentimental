-- PharmaTrust Production Upgrade - Initial Database Schema
-- Migration V1: Create all tables with indexes and constraints

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- USERS TABLE
-- ============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('MANUFACTURER', 'DISTRIBUTOR', 'PHARMACIST', 'REGULATOR', 'PRODUCTION_HEAD', 'QUALITY_CHECKER')),
    manufacturer_id VARCHAR(255),
    public_key TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_manufacturer_id ON users(manufacturer_id);

-- ============================================
-- BATCHES TABLE
-- ============================================
CREATE TABLE batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_number VARCHAR(255) UNIQUE NOT NULL,
    medicine_name VARCHAR(255) NOT NULL,
    manufacturing_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    manufacturer_id UUID NOT NULL REFERENCES users(id),
    lab_report_hash VARCHAR(255),
    lab_report_s3_key VARCHAR(500),
    blockchain_tx_id VARCHAR(255),
    merkle_root VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING', 'PENDING_APPROVAL', 'ACTIVE', 'QUARANTINE', 'RECALLED', 'RECALLED_AUTO')),
    total_units INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    digital_signature TEXT,
    expiry_alert_sent BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_warning_date DATE
);

CREATE INDEX idx_batches_batch_number ON batches(batch_number);
CREATE INDEX idx_batches_manufacturer_id ON batches(manufacturer_id);
CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batches_expiry_date ON batches(expiry_date);

-- ============================================
-- UNIT_ITEMS TABLE
-- ============================================
CREATE TABLE unit_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    serial_number VARCHAR(255) UNIQUE NOT NULL,
    batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
    parent_unit_id UUID REFERENCES unit_items(id),
    unit_type VARCHAR(50) CHECK (unit_type IN ('TABLET', 'STRIP', 'BOX', 'CARTON')),
    qr_payload_encrypted TEXT,
    digital_signature TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'RECALLED', 'RECALLED_AUTO', 'EXPIRED')),
    scan_count INTEGER NOT NULL DEFAULT 0,
    max_scan_limit INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    first_scanned_at TIMESTAMP,
    last_scanned_at TIMESTAMP,
    current_owner_id UUID REFERENCES users(id)
);

CREATE INDEX idx_unit_items_serial_number ON unit_items(serial_number);
CREATE INDEX idx_unit_items_batch_id ON unit_items(batch_id);
CREATE INDEX idx_unit_items_parent_unit_id ON unit_items(parent_unit_id);
CREATE INDEX idx_unit_items_status ON unit_items(status);
CREATE INDEX idx_unit_items_current_owner_id ON unit_items(current_owner_id);

-- ============================================
-- SCAN_LOGS TABLE
-- ============================================
CREATE TABLE scan_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    unit_id UUID NOT NULL REFERENCES unit_items(id) ON DELETE CASCADE,
    scanned_by_user_id UUID REFERENCES users(id),
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    location_lat VARCHAR(50),
    location_lng VARCHAR(50),
    device_info TEXT,
    ip_address VARCHAR(50),
    device_fingerprint VARCHAR(255),
    scan_result VARCHAR(50) NOT NULL CHECK (scan_result IN ('VALID', 'INVALID', 'SUSPICIOUS', 'FLAGGED')),
    anomaly_score FLOAT DEFAULT 0.0,
    auto_flagged BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_scan_logs_unit_id ON scan_logs(unit_id);
CREATE INDEX idx_scan_logs_scanned_at ON scan_logs(scanned_at);
CREATE INDEX idx_scan_logs_scan_result ON scan_logs(scan_result);
CREATE INDEX idx_scan_logs_anomaly_score ON scan_logs(anomaly_score);

-- ============================================
-- BATCH_APPROVALS TABLE
-- ============================================
CREATE TABLE batch_approvals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
    approver_id UUID NOT NULL REFERENCES users(id),
    approval_type VARCHAR(50) NOT NULL CHECK (approval_type IN ('PRODUCTION_HEAD', 'QUALITY_CHECKER', 'REGULATOR')),
    digital_signature TEXT NOT NULL,
    approved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_batch_approvals_batch_id ON batch_approvals(batch_id);
CREATE INDEX idx_batch_approvals_approver_id ON batch_approvals(approver_id);

-- ============================================
-- RECALL_EVENTS TABLE
-- ============================================
CREATE TABLE recall_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
    initiated_by UUID NOT NULL REFERENCES users(id),
    recall_type VARCHAR(50) NOT NULL CHECK (recall_type IN ('MANUAL', 'AUTO', 'EMERGENCY')),
    reason TEXT NOT NULL,
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_recall_events_batch_id ON recall_events(batch_id);
CREATE INDEX idx_recall_events_initiated_by ON recall_events(initiated_by);
CREATE INDEX idx_recall_events_initiated_at ON recall_events(initiated_at);

-- ============================================
-- OWNERSHIP_LOGS TABLE
-- ============================================
CREATE TABLE ownership_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    unit_id UUID NOT NULL REFERENCES unit_items(id) ON DELETE CASCADE,
    from_user_id UUID REFERENCES users(id),
    to_user_id UUID NOT NULL REFERENCES users(id),
    transfer_type VARCHAR(50) NOT NULL CHECK (transfer_type IN ('MANUFACTURE_TO_DISTRIBUTOR', 'DISTRIBUTOR_TO_PHARMACY', 'PHARMACY_TO_PATIENT')),
    transferred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    location VARCHAR(255),
    notes TEXT
);

CREATE INDEX idx_ownership_logs_unit_id ON ownership_logs(unit_id);
CREATE INDEX idx_ownership_logs_from_user_id ON ownership_logs(from_user_id);
CREATE INDEX idx_ownership_logs_to_user_id ON ownership_logs(to_user_id);
CREATE INDEX idx_ownership_logs_transferred_at ON ownership_logs(transferred_at);

-- ============================================
-- BATCH_JOBS TABLE
-- ============================================
CREATE TABLE batch_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id UUID REFERENCES batches(id) ON DELETE CASCADE,
    job_type VARCHAR(50) NOT NULL CHECK (job_type IN ('UNIT_GENERATION', 'QR_GENERATION', 'BLOCKCHAIN_MINT')),
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    total_items INTEGER NOT NULL DEFAULT 0,
    processed_items INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_batch_jobs_batch_id ON batch_jobs(batch_id);
CREATE INDEX idx_batch_jobs_status ON batch_jobs(status);
CREATE INDEX idx_batch_jobs_job_type ON batch_jobs(job_type);

-- ============================================
-- AUDIT_LOGS TABLE (for compliance)
-- ============================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    details JSONB,
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX idx_audit_logs_entity_id ON audit_logs(entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- ============================================
-- ALERTS TABLE (for AI Sentinel)
-- ============================================
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_type VARCHAR(50) NOT NULL CHECK (alert_type IN ('GEOGRAPHIC_FRAUD', 'SUSPICIOUS_SCAN', 'EXPIRY_WARNING', 'RECALL_NOTIFICATION', 'TAMPERING_DETECTED')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    message TEXT NOT NULL,
    entity_id UUID,
    entity_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by UUID REFERENCES users(id),
    acknowledged_at TIMESTAMP
);

CREATE INDEX idx_alerts_alert_type ON alerts(alert_type);
CREATE INDEX idx_alerts_severity ON alerts(severity);
CREATE INDEX idx_alerts_created_at ON alerts(created_at);
CREATE INDEX idx_alerts_acknowledged ON alerts(acknowledged);
