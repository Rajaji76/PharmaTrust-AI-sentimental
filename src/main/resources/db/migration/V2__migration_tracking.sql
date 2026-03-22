-- PharmaTrust Production Upgrade - Migration Tracking
-- Migration V2: Add migration tracking table for SQLite-to-PostgreSQL data migration

-- ============================================
-- MIGRATION_LOG TABLE
-- Tracks the status of data migration from SQLite to PostgreSQL
-- ============================================
CREATE TABLE IF NOT EXISTS migration_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    migration_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    source_count INTEGER NOT NULL DEFAULT 0,
    migrated_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'VERIFIED')),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    verified_at TIMESTAMP,
    error_details TEXT,
    checksum VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_migration_log_entity_type ON migration_log(entity_type);
CREATE INDEX idx_migration_log_status ON migration_log(status);

COMMENT ON TABLE migration_log IS 'Tracks SQLite to PostgreSQL data migration progress and integrity';
