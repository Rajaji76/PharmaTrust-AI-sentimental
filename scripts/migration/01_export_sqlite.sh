#!/bin/bash
# =============================================================================
# PharmaTrust - SQLite Data Export Script
# Task 21.1: Export existing batch data from SQLite
# Requirements: DR-001
# =============================================================================
# Usage: ./01_export_sqlite.sh [SQLITE_DB_PATH] [OUTPUT_DIR]
# Example: ./01_export_sqlite.sh ./pharmatrust.db ./migration_export
# =============================================================================

set -euo pipefail

SQLITE_DB="${1:-./pharmatrust.db}"
OUTPUT_DIR="${2:-./migration_export}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="${OUTPUT_DIR}/export_${TIMESTAMP}.log"

# ---- Colour helpers ----
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date '+%H:%M:%S')] $*${NC}" | tee -a "$LOG_FILE"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARN: $*${NC}" | tee -a "$LOG_FILE"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ERROR: $*${NC}" | tee -a "$LOG_FILE"; }

# ---- Pre-flight checks ----
if [ ! -f "$SQLITE_DB" ]; then
    warn "SQLite database not found at: $SQLITE_DB"
    warn "Creating sample export structure for schema validation purposes."
    mkdir -p "$OUTPUT_DIR"
    touch "$LOG_FILE"
    # Create empty CSV stubs so downstream scripts can run in dry-run mode
    for table in users batches unit_items scan_logs batch_approvals recall_events ownership_logs batch_jobs; do
        touch "${OUTPUT_DIR}/${table}.csv"
    done
    log "Empty export stubs created in $OUTPUT_DIR (no source SQLite DB found)"
    exit 0
fi

command -v sqlite3 >/dev/null 2>&1 || { err "sqlite3 is required but not installed."; exit 1; }

mkdir -p "$OUTPUT_DIR"
touch "$LOG_FILE"

log "============================================"
log "PharmaTrust SQLite Export"
log "Source DB : $SQLITE_DB"
log "Output Dir: $OUTPUT_DIR"
log "Timestamp : $TIMESTAMP"
log "============================================"

# ---- Helper: export a table to CSV ----
export_table() {
    local table="$1"
    local out_file="${OUTPUT_DIR}/${table}.csv"

    log "Exporting table: $table"

    # Count rows first
    local count
    count=$(sqlite3 "$SQLITE_DB" "SELECT COUNT(*) FROM ${table};" 2>/dev/null || echo "0")

    if [ "$count" -eq 0 ]; then
        warn "Table '$table' is empty or does not exist — skipping"
        touch "$out_file"
        return
    fi

    # Export with headers
    sqlite3 -header -csv "$SQLITE_DB" "SELECT * FROM ${table};" > "$out_file"
    log "  Exported $count rows → $out_file"
}

# ---- Export all tables ----
# Order matters: parents before children (FK constraints)
TABLES=(users batches unit_items scan_logs batch_approvals recall_events ownership_logs batch_jobs)

for table in "${TABLES[@]}"; do
    export_table "$table" || warn "Failed to export $table — continuing"
done

# ---- Generate row-count summary ----
SUMMARY_FILE="${OUTPUT_DIR}/export_summary_${TIMESTAMP}.txt"
{
    echo "PharmaTrust SQLite Export Summary"
    echo "Generated: $(date)"
    echo "Source DB: $SQLITE_DB"
    echo "-----------------------------------"
    for table in "${TABLES[@]}"; do
        csv="${OUTPUT_DIR}/${table}.csv"
        if [ -f "$csv" ]; then
            # Subtract 1 for header row
            lines=$(wc -l < "$csv")
            rows=$(( lines > 0 ? lines - 1 : 0 ))
            echo "$table: $rows rows"
        else
            echo "$table: NOT EXPORTED"
        fi
    done
} > "$SUMMARY_FILE"

log "Export summary written to: $SUMMARY_FILE"
log "Export completed successfully."
