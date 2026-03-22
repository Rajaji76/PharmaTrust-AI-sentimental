#!/bin/bash
# =============================================================================
# PharmaTrust - Master Migration Runner
# Task 21.1: SQLite to PostgreSQL full migration pipeline
# Task 21.2: Lab report migration to AWS S3 (optional Step 4)
# Requirements: DR-001, DR-002
# =============================================================================
# Usage:
#   ./run_migration.sh [OPTIONS]
#
# Environment variables (override defaults):
#   SQLITE_DB           Path to SQLite database file (default: ./pharmatrust.db)
#   EXPORT_DIR          CSV export directory (default: ./migration_export)
#   PG_HOST             PostgreSQL host (default: localhost)
#   PG_PORT             PostgreSQL port (default: 5432)
#   PG_DB               PostgreSQL database (default: pharmatrust_db)
#   PG_USER             PostgreSQL user (default: postgres)
#   PG_PASSWORD         PostgreSQL password
#   DRY_RUN             Set to "true" to validate without writing (default: false)
#   BATCH_SIZE          Insert batch size (default: 500)
#   SKIP_EXPORT         Set to "true" to skip SQLite export step (default: false)
#   SKIP_VERIFY         Set to "true" to skip integrity verification (default: false)
#
#   --- Step 4: Lab Report S3 Migration (optional) ---
#   RUN_S3_MIGRATION    Set to "true" to run lab report S3 migration (default: false)
#   LAB_REPORTS_DIR     Local directory with lab report files (default: ./lab_reports)
#   LAB_ARCHIVE_DIR     Archive directory after upload (default: ./lab_reports/archived)
#   S3_BUCKET_NAME      Target S3 bucket (default: pharmatrust-lab-reports)
#   S3_PREFIX           S3 key prefix (default: lab-reports)
#   AWS_REGION          AWS region (default: us-east-1)
#   AWS_ACCESS_KEY_ID   AWS access key (falls back to IAM role if unset)
#   AWS_SECRET_ACCESS_KEY  AWS secret key (falls back to IAM role if unset)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Configuration ──────────────────────────────────────────────────────────────
SQLITE_DB="${SQLITE_DB:-./pharmatrust.db}"
EXPORT_DIR="${EXPORT_DIR:-./migration_export}"
PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_DB="${PG_DB:-pharmatrust_db}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-}"
DRY_RUN="${DRY_RUN:-false}"
BATCH_SIZE="${BATCH_SIZE:-500}"
SKIP_EXPORT="${SKIP_EXPORT:-false}"
SKIP_VERIFY="${SKIP_VERIFY:-false}"

# Step 4 — Lab Report S3 Migration (optional)
RUN_S3_MIGRATION="${RUN_S3_MIGRATION:-false}"
LAB_REPORTS_DIR="${LAB_REPORTS_DIR:-./lab_reports}"
LAB_ARCHIVE_DIR="${LAB_ARCHIVE_DIR:-./lab_reports/archived}"
S3_BUCKET_NAME="${S3_BUCKET_NAME:-pharmatrust-lab-reports}"
S3_PREFIX="${S3_PREFIX:-lab-reports}"
AWS_REGION="${AWS_REGION:-us-east-1}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
MASTER_LOG="${EXPORT_DIR}/migration_master_${TIMESTAMP}.log"

# ── Colour helpers ──────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date '+%H:%M:%S')] $*${NC}" | tee -a "$MASTER_LOG"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARN: $*${NC}" | tee -a "$MASTER_LOG"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ERROR: $*${NC}" | tee -a "$MASTER_LOG"; }
info() { echo -e "${BLUE}[$(date '+%H:%M:%S')] $*${NC}" | tee -a "$MASTER_LOG"; }

mkdir -p "$EXPORT_DIR"
touch "$MASTER_LOG"

log "╔══════════════════════════════════════════════════════╗"
log "║   PharmaTrust SQLite → PostgreSQL Migration          ║"
log "╚══════════════════════════════════════════════════════╝"
info "SQLite DB  : $SQLITE_DB"
info "Export Dir : $EXPORT_DIR"
info "PG Target  : $PG_USER@$PG_HOST:$PG_PORT/$PG_DB"
info "Dry Run    : $DRY_RUN"
info "Batch Size : $BATCH_SIZE"
log ""

# ── Step 1: Export SQLite data ─────────────────────────────────────────────────
if [ "$SKIP_EXPORT" = "true" ]; then
    warn "Skipping SQLite export (SKIP_EXPORT=true)"
else
    log "Step 1/3: Exporting SQLite data..."
    bash "${SCRIPT_DIR}/01_export_sqlite.sh" "$SQLITE_DB" "$EXPORT_DIR"
    log "Step 1/3: Export complete."
fi

# ── Step 2: Migrate to PostgreSQL ─────────────────────────────────────────────
log ""
log "Step 2/3: Migrating data to PostgreSQL..."

MIGRATE_ARGS=(
    "--export-dir" "$EXPORT_DIR"
    "--pg-host" "$PG_HOST"
    "--pg-port" "$PG_PORT"
    "--pg-db" "$PG_DB"
    "--pg-user" "$PG_USER"
    "--batch-size" "$BATCH_SIZE"
)

if [ -n "$PG_PASSWORD" ]; then
    MIGRATE_ARGS+=("--pg-password" "$PG_PASSWORD")
fi

if [ "$DRY_RUN" = "true" ]; then
    MIGRATE_ARGS+=("--dry-run")
    warn "DRY RUN mode — no data will be written to PostgreSQL"
fi

python3 "${SCRIPT_DIR}/02_migrate_to_postgres.py" "${MIGRATE_ARGS[@]}"
log "Step 2/3: Migration complete."

# ── Step 3: Verify integrity ───────────────────────────────────────────────────
if [ "$SKIP_VERIFY" = "true" ]; then
    warn "Skipping integrity verification (SKIP_VERIFY=true)"
elif [ "$DRY_RUN" = "true" ]; then
    warn "Skipping integrity verification in dry-run mode"
else
    log ""
    log "Step 3/3: Verifying data integrity..."

    VERIFY_ARGS=(
        "--export-dir" "$EXPORT_DIR"
        "--pg-host" "$PG_HOST"
        "--pg-port" "$PG_PORT"
        "--pg-db" "$PG_DB"
        "--pg-user" "$PG_USER"
    )

    if [ -n "$PG_PASSWORD" ]; then
        VERIFY_ARGS+=("--pg-password" "$PG_PASSWORD")
    fi

    python3 "${SCRIPT_DIR}/03_verify_integrity.py" "${VERIFY_ARGS[@]}"
    log "Step 3/3: Integrity verification complete."
fi

# ── Step 4: Migrate lab reports to S3 (optional) ──────────────────────────────
if [ "$RUN_S3_MIGRATION" = "true" ]; then
    log ""
    log "Step 4/4: Migrating lab reports to AWS S3..."

    S3_ARGS=(
        "--lab-reports-dir" "$LAB_REPORTS_DIR"
        "--archive-dir" "$LAB_ARCHIVE_DIR"
        "--s3-bucket" "$S3_BUCKET_NAME"
        "--s3-prefix" "$S3_PREFIX"
        "--aws-region" "$AWS_REGION"
        "--pg-host" "$PG_HOST"
        "--pg-port" "$PG_PORT"
        "--pg-db" "$PG_DB"
        "--pg-user" "$PG_USER"
    )

    if [ -n "$PG_PASSWORD" ]; then
        S3_ARGS+=("--pg-password" "$PG_PASSWORD")
    fi

    if [ "$DRY_RUN" = "true" ]; then
        S3_ARGS+=("--dry-run")
        warn "DRY RUN mode — no files will be uploaded or archived"
    fi

    python3 "${SCRIPT_DIR}/04_migrate_lab_reports_s3.py" "${S3_ARGS[@]}"
    log "Step 4/4: Lab report S3 migration complete."
else
    warn "Skipping lab report S3 migration (set RUN_S3_MIGRATION=true to enable)"
fi

log ""
log "╔══════════════════════════════════════════════════════╗"
log "║   Migration pipeline finished successfully           ║"
log "╚══════════════════════════════════════════════════════╝"
log "Master log: $MASTER_LOG"
