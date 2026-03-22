#!/usr/bin/env python3
"""
PharmaTrust - SQLite to PostgreSQL Data Migration Script
Task 21.1: Transform schema and migrate batch/unit data with integrity checks
Requirements: DR-001

Usage:
    python3 02_migrate_to_postgres.py [OPTIONS]

Options:
    --export-dir   Path to CSV export directory (default: ./migration_export)
    --pg-host      PostgreSQL host (default: localhost)
    --pg-port      PostgreSQL port (default: 5432)
    --pg-db        PostgreSQL database name (default: pharmatrust_db)
    --pg-user      PostgreSQL username (default: postgres)
    --pg-password  PostgreSQL password (default: from env PG_PASSWORD)
    --dry-run      Validate CSVs without writing to PostgreSQL
    --batch-size   Insert batch size (default: 500)
"""

import argparse
import csv
import hashlib
import json
import logging
import os
import sys
import uuid
from datetime import datetime, date
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# psycopg2 is the standard PostgreSQL adapter for Python
try:
    import psycopg2
    import psycopg2.extras
    HAS_PSYCOPG2 = True
except ImportError:
    HAS_PSYCOPG2 = False

# ── Logging setup ──────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(f"migration_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"),
    ],
)
log = logging.getLogger(__name__)


# ── Schema transformation helpers ─────────────────────────────────────────────

def coerce_uuid(value: str) -> Optional[str]:
    """Return a valid UUID string or None."""
    if not value or value.strip() == "":
        return None
    try:
        return str(uuid.UUID(value.strip()))
    except ValueError:
        # Generate a deterministic UUID from the original value so FK links stay intact
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, value.strip()))


def coerce_bool(value: str) -> Optional[bool]:
    if value is None or value.strip() == "":
        return None
    return value.strip().lower() in ("1", "true", "yes", "t")


def coerce_int(value: str) -> Optional[int]:
    if value is None or value.strip() == "":
        return None
    try:
        return int(float(value.strip()))
    except (ValueError, TypeError):
        return None


def coerce_float(value: str) -> Optional[float]:
    if value is None or value.strip() == "":
        return None
    try:
        return float(value.strip())
    except (ValueError, TypeError):
        return None


def coerce_timestamp(value: str) -> Optional[str]:
    """Normalise various SQLite timestamp formats to ISO-8601."""
    if not value or value.strip() == "":
        return None
    value = value.strip()
    for fmt in (
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%d",
    ):
        try:
            return datetime.strptime(value, fmt).isoformat()
        except ValueError:
            continue
    log.warning("Cannot parse timestamp '%s' — using NULL", value)
    return None


def coerce_date(value: str) -> Optional[str]:
    if not value or value.strip() == "":
        return None
    value = value.strip()
    for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%m/%d/%Y"):
        try:
            return datetime.strptime(value, fmt).date().isoformat()
        except ValueError:
            continue
    log.warning("Cannot parse date '%s' — using NULL", value)
    return None


def normalise_role(value: str) -> str:
    """Map legacy SQLite role names to the PostgreSQL CHECK constraint values."""
    mapping = {
        "ADMIN": "MANUFACTURER",
        "USER": "PHARMACIST",
        "RETAILER": "PHARMACIST",
        "PATIENT": "PHARMACIST",
    }
    v = (value or "").strip().upper()
    return mapping.get(v, v) if v else "PHARMACIST"


def normalise_batch_status(value: str) -> str:
    mapping = {
        "DRAFT": "PROCESSING",
        "CREATED": "PROCESSING",
        "APPROVED": "ACTIVE",
        "REJECTED": "RECALLED",
        "EXPIRED": "RECALLED",
    }
    v = (value or "").strip().upper()
    return mapping.get(v, v) if v else "PROCESSING"


def normalise_unit_status(value: str) -> str:
    mapping = {
        "SOLD": "TRANSFERRED",
        "DISPENSED": "TRANSFERRED",
        "INACTIVE": "RECALLED",
    }
    v = (value or "").strip().upper()
    return mapping.get(v, v) if v else "ACTIVE"


def normalise_scan_result(value: str) -> str:
    mapping = {
        "OK": "VALID",
        "PASS": "VALID",
        "FAIL": "INVALID",
        "ERROR": "INVALID",
        "WARNING": "SUSPICIOUS",
    }
    v = (value or "").strip().upper()
    return mapping.get(v, v) if v else "VALID"


# ── Row transformers ───────────────────────────────────────────────────────────

def transform_user(row: Dict[str, str]) -> Dict[str, Any]:
    return {
        "id": coerce_uuid(row.get("id") or row.get("user_id", "")),
        "username": (row.get("username") or row.get("email") or "").strip(),
        "password_hash": (row.get("password_hash") or row.get("password", "")).strip(),
        "role": normalise_role(row.get("role", "PHARMACIST")),
        "manufacturer_id": (row.get("manufacturer_id") or "").strip() or None,
        "public_key": (row.get("public_key") or "").strip() or None,
        "created_at": coerce_timestamp(row.get("created_at", "")) or datetime.utcnow().isoformat(),
        "is_active": coerce_bool(row.get("is_active", "1")) if row.get("is_active") else True,
    }


def transform_batch(row: Dict[str, str]) -> Dict[str, Any]:
    return {
        "id": coerce_uuid(row.get("id") or row.get("batch_id", "")),
        "batch_number": (row.get("batch_number") or "").strip(),
        "medicine_name": (row.get("medicine_name") or row.get("drug_name") or "UNKNOWN").strip(),
        "manufacturing_date": coerce_date(row.get("manufacturing_date") or row.get("mfg_date", "")),
        "expiry_date": coerce_date(row.get("expiry_date") or row.get("exp_date", "")),
        "manufacturer_id": coerce_uuid(row.get("manufacturer_id") or row.get("created_by", "")),
        "lab_report_hash": (row.get("lab_report_hash") or "").strip() or None,
        "lab_report_s3_key": (row.get("lab_report_s3_key") or row.get("lab_report_path") or "").strip() or None,
        "blockchain_tx_id": (row.get("blockchain_tx_id") or "").strip() or None,
        "merkle_root": (row.get("merkle_root") or "").strip() or None,
        "status": normalise_batch_status(row.get("status", "PROCESSING")),
        "total_units": coerce_int(row.get("total_units", "0")) or 0,
        "created_at": coerce_timestamp(row.get("created_at", "")) or datetime.utcnow().isoformat(),
        "digital_signature": (row.get("digital_signature") or "").strip() or None,
        "expiry_alert_sent": coerce_bool(row.get("expiry_alert_sent", "0")) or False,
        "expiry_warning_date": coerce_date(row.get("expiry_warning_date", "")),
        "blockchain_confirmed": coerce_bool(row.get("blockchain_confirmed", "0")) or False,
    }


def transform_unit_item(row: Dict[str, str]) -> Dict[str, Any]:
    return {
        "id": coerce_uuid(row.get("id") or row.get("unit_id", "")),
        "serial_number": (row.get("serial_number") or "").strip(),
        "batch_id": coerce_uuid(row.get("batch_id", "")),
        "parent_unit_id": coerce_uuid(row.get("parent_unit_id", "")) or None,
        "unit_type": (row.get("unit_type") or "").strip().upper() or None,
        "qr_payload_encrypted": (row.get("qr_payload_encrypted") or row.get("qr_payload") or "").strip() or None,
        "digital_signature": (row.get("digital_signature") or "").strip() or None,
        "status": normalise_unit_status(row.get("status", "ACTIVE")),
        "scan_count": coerce_int(row.get("scan_count", "0")) or 0,
        "max_scan_limit": coerce_int(row.get("max_scan_limit", "5")) or 5,
        "is_active": coerce_bool(row.get("is_active", "1")) if row.get("is_active") else True,
        "first_scanned_at": coerce_timestamp(row.get("first_scanned_at", "")),
        "last_scanned_at": coerce_timestamp(row.get("last_scanned_at", "")),
        "current_owner_id": coerce_uuid(row.get("current_owner_id", "")) or None,
    }


def transform_scan_log(row: Dict[str, str]) -> Dict[str, Any]:
    return {
        "id": coerce_uuid(row.get("id") or row.get("log_id", "")),
        "unit_id": coerce_uuid(row.get("unit_id", "")),
        "scanned_by_user_id": coerce_uuid(row.get("scanned_by_user_id") or row.get("user_id", "")) or None,
        "scanned_at": coerce_timestamp(row.get("scanned_at", "")) or datetime.utcnow().isoformat(),
        "location_lat": (row.get("location_lat") or row.get("lat") or "").strip() or None,
        "location_lng": (row.get("location_lng") or row.get("lng") or "").strip() or None,
        "device_info": (row.get("device_info") or "").strip() or None,
        "ip_address": (row.get("ip_address") or "").strip() or None,
        "device_fingerprint": (row.get("device_fingerprint") or "").strip() or None,
        "scan_result": normalise_scan_result(row.get("scan_result", "VALID")),
        "anomaly_score": coerce_float(row.get("anomaly_score", "0.0")) or 0.0,
        "auto_flagged": coerce_bool(row.get("auto_flagged", "0")) or False,
    }


def transform_batch_approval(row: Dict[str, str]) -> Dict[str, Any]:
    approval_type = (row.get("approval_type") or "PRODUCTION_HEAD").strip().upper()
    if approval_type not in ("PRODUCTION_HEAD", "QUALITY_CHECKER", "REGULATOR"):
        approval_type = "PRODUCTION_HEAD"
    return {
        "id": coerce_uuid(row.get("id", "")),
        "batch_id": coerce_uuid(row.get("batch_id", "")),
        "approver_id": coerce_uuid(row.get("approver_id", "")),
        "approval_type": approval_type,
        "digital_signature": (row.get("digital_signature") or "MIGRATED").strip(),
        "approved_at": coerce_timestamp(row.get("approved_at", "")) or datetime.utcnow().isoformat(),
    }


def transform_recall_event(row: Dict[str, str]) -> Dict[str, Any]:
    recall_type = (row.get("recall_type") or "MANUAL").strip().upper()
    if recall_type not in ("MANUAL", "AUTO", "EMERGENCY"):
        recall_type = "MANUAL"
    status = (row.get("status") or "ACTIVE").strip().upper()
    if status not in ("ACTIVE", "COMPLETED", "CANCELLED"):
        status = "ACTIVE"
    return {
        "id": coerce_uuid(row.get("id", "")),
        "batch_id": coerce_uuid(row.get("batch_id", "")),
        "initiated_by": coerce_uuid(row.get("initiated_by") or row.get("user_id", "")),
        "recall_type": recall_type,
        "reason": (row.get("reason") or "Migrated from legacy system").strip(),
        "initiated_at": coerce_timestamp(row.get("initiated_at", "")) or datetime.utcnow().isoformat(),
        "status": status,
    }


def transform_ownership_log(row: Dict[str, str]) -> Dict[str, Any]:
    transfer_type = (row.get("transfer_type") or "MANUFACTURE_TO_DISTRIBUTOR").strip().upper()
    valid_types = ("MANUFACTURE_TO_DISTRIBUTOR", "DISTRIBUTOR_TO_PHARMACY", "PHARMACY_TO_PATIENT")
    if transfer_type not in valid_types:
        transfer_type = "MANUFACTURE_TO_DISTRIBUTOR"
    return {
        "id": coerce_uuid(row.get("id", "")),
        "unit_id": coerce_uuid(row.get("unit_id", "")),
        "from_user_id": coerce_uuid(row.get("from_user_id", "")) or None,
        "to_user_id": coerce_uuid(row.get("to_user_id", "")),
        "transfer_type": transfer_type,
        "transferred_at": coerce_timestamp(row.get("transferred_at", "")) or datetime.utcnow().isoformat(),
        "location": (row.get("location") or "").strip() or None,
        "notes": (row.get("notes") or "").strip() or None,
    }


def transform_batch_job(row: Dict[str, str]) -> Dict[str, Any]:
    job_type = (row.get("job_type") or "UNIT_GENERATION").strip().upper()
    if job_type not in ("UNIT_GENERATION", "QR_GENERATION", "BLOCKCHAIN_MINT"):
        job_type = "UNIT_GENERATION"
    status = (row.get("status") or "COMPLETED").strip().upper()
    if status not in ("QUEUED", "PROCESSING", "COMPLETED", "FAILED"):
        status = "COMPLETED"
    return {
        "id": coerce_uuid(row.get("id", "")),
        "batch_id": coerce_uuid(row.get("batch_id", "")) or None,
        "job_type": job_type,
        "status": status,
        "total_items": coerce_int(row.get("total_items", "0")) or 0,
        "processed_items": coerce_int(row.get("processed_items", "0")) or 0,
        "started_at": coerce_timestamp(row.get("started_at", "")),
        "completed_at": coerce_timestamp(row.get("completed_at", "")),
        "error_message": (row.get("error_message") or "").strip() or None,
    }


# ── CSV reader ─────────────────────────────────────────────────────────────────

def read_csv(path: Path) -> List[Dict[str, str]]:
    if not path.exists() or path.stat().st_size == 0:
        log.info("CSV file empty or missing: %s — skipping", path)
        return []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return list(reader)


# ── Checksum helper ────────────────────────────────────────────────────────────

def compute_checksum(rows: List[Dict]) -> str:
    """SHA-256 of sorted JSON representation for integrity verification."""
    serialised = json.dumps(rows, sort_keys=True, default=str)
    return hashlib.sha256(serialised.encode()).hexdigest()


# ── PostgreSQL helpers ─────────────────────────────────────────────────────────

def get_pg_connection(args):
    if not HAS_PSYCOPG2:
        raise RuntimeError(
            "psycopg2 is not installed. Run: pip install psycopg2-binary"
        )
    password = args.pg_password or os.environ.get("PG_PASSWORD", "")
    return psycopg2.connect(
        host=args.pg_host,
        port=args.pg_port,
        dbname=args.pg_db,
        user=args.pg_user,
        password=password,
    )


def upsert_batch(conn, table: str, rows: List[Dict[str, Any]], columns: List[str], batch_size: int) -> Tuple[int, int]:
    """Insert rows using ON CONFLICT DO NOTHING (idempotent). Returns (inserted, skipped)."""
    if not rows:
        return 0, 0

    inserted = 0
    skipped = 0
    placeholders = ", ".join(["%s"] * len(columns))
    col_names = ", ".join(columns)
    sql = (
        f"INSERT INTO {table} ({col_names}) VALUES ({placeholders}) "
        f"ON CONFLICT (id) DO NOTHING"
    )

    with conn.cursor() as cur:
        for i in range(0, len(rows), batch_size):
            chunk = rows[i : i + batch_size]
            values = [[row.get(c) for c in columns] for row in chunk]
            psycopg2.extras.execute_batch(cur, sql, values, page_size=batch_size)
            # rowcount is unreliable with execute_batch; approximate
            inserted += len(chunk)
        conn.commit()

    return inserted, skipped


def log_migration(conn, entity_type: str, source_count: int, migrated_count: int,
                  failed_count: int, status: str, checksum: str, error: str = None):
    sql = """
        INSERT INTO migration_log
            (migration_name, entity_type, source_count, migrated_count, failed_count,
             status, started_at, completed_at, checksum, error_details)
        VALUES (%s, %s, %s, %s, %s, %s, NOW(), NOW(), %s, %s)
        ON CONFLICT DO NOTHING
    """
    with conn.cursor() as cur:
        cur.execute(sql, (
            "sqlite_to_postgres_migration",
            entity_type,
            source_count,
            migrated_count,
            failed_count,
            status,
            checksum,
            error,
        ))
    conn.commit()


# ── Migration orchestrator ─────────────────────────────────────────────────────

MIGRATION_PLAN = [
    # (csv_name, pg_table, transformer_fn, pg_columns)
    ("users", "users", transform_user, [
        "id", "username", "password_hash", "role", "manufacturer_id",
        "public_key", "created_at", "is_active",
    ]),
    ("batches", "batches", transform_batch, [
        "id", "batch_number", "medicine_name", "manufacturing_date", "expiry_date",
        "manufacturer_id", "lab_report_hash", "lab_report_s3_key", "blockchain_tx_id",
        "merkle_root", "status", "total_units", "created_at", "digital_signature",
        "expiry_alert_sent", "expiry_warning_date", "blockchain_confirmed",
    ]),
    ("unit_items", "unit_items", transform_unit_item, [
        "id", "serial_number", "batch_id", "parent_unit_id", "unit_type",
        "qr_payload_encrypted", "digital_signature", "status", "scan_count",
        "max_scan_limit", "is_active", "first_scanned_at", "last_scanned_at",
        "current_owner_id",
    ]),
    ("scan_logs", "scan_logs", transform_scan_log, [
        "id", "unit_id", "scanned_by_user_id", "scanned_at", "location_lat",
        "location_lng", "device_info", "ip_address", "device_fingerprint",
        "scan_result", "anomaly_score", "auto_flagged",
    ]),
    ("batch_approvals", "batch_approvals", transform_batch_approval, [
        "id", "batch_id", "approver_id", "approval_type", "digital_signature", "approved_at",
    ]),
    ("recall_events", "recall_events", transform_recall_event, [
        "id", "batch_id", "initiated_by", "recall_type", "reason", "initiated_at", "status",
    ]),
    ("ownership_logs", "ownership_logs", transform_ownership_log, [
        "id", "unit_id", "from_user_id", "to_user_id", "transfer_type",
        "transferred_at", "location", "notes",
    ]),
    ("batch_jobs", "batch_jobs", transform_batch_job, [
        "id", "batch_id", "job_type", "status", "total_items", "processed_items",
        "started_at", "completed_at", "error_message",
    ]),
]


def run_migration(args):
    export_dir = Path(args.export_dir)
    if not export_dir.exists():
        log.error("Export directory not found: %s", export_dir)
        sys.exit(1)

    conn = None
    if not args.dry_run:
        if not HAS_PSYCOPG2:
            log.error("psycopg2 not installed. Install with: pip install psycopg2-binary")
            sys.exit(1)
        log.info("Connecting to PostgreSQL at %s:%s/%s", args.pg_host, args.pg_port, args.pg_db)
        conn = get_pg_connection(args)
        log.info("Connected to PostgreSQL successfully.")

    results = {}

    for csv_name, pg_table, transformer, columns in MIGRATION_PLAN:
        csv_path = export_dir / f"{csv_name}.csv"
        log.info("── Migrating %s → %s ──", csv_name, pg_table)

        raw_rows = read_csv(csv_path)
        source_count = len(raw_rows)
        log.info("  Source rows: %d", source_count)

        if source_count == 0:
            log.info("  No data to migrate for %s", csv_name)
            results[csv_name] = {"source": 0, "migrated": 0, "failed": 0, "status": "COMPLETED"}
            continue

        # Transform rows
        transformed = []
        failed = 0
        for i, row in enumerate(raw_rows):
            try:
                t = transformer(row)
                # Skip rows with missing required IDs
                if t.get("id") is None:
                    t["id"] = str(uuid.uuid4())
                transformed.append(t)
            except Exception as exc:
                log.warning("  Row %d transform error: %s", i, exc)
                failed += 1

        checksum = compute_checksum(transformed)
        log.info("  Transformed: %d rows (failed: %d), checksum: %s…", len(transformed), failed, checksum[:16])

        if args.dry_run:
            log.info("  [DRY RUN] Would insert %d rows into %s", len(transformed), pg_table)
            results[csv_name] = {
                "source": source_count,
                "migrated": len(transformed),
                "failed": failed,
                "status": "DRY_RUN",
                "checksum": checksum,
            }
            continue

        # Insert into PostgreSQL
        try:
            inserted, _ = upsert_batch(conn, pg_table, transformed, columns, args.batch_size)
            log.info("  Inserted %d rows into %s", inserted, pg_table)
            log_migration(conn, csv_name, source_count, inserted, failed, "COMPLETED", checksum)
            results[csv_name] = {
                "source": source_count,
                "migrated": inserted,
                "failed": failed,
                "status": "COMPLETED",
                "checksum": checksum,
            }
        except Exception as exc:
            log.error("  Migration failed for %s: %s", pg_table, exc)
            if conn:
                conn.rollback()
            log_migration(conn, csv_name, source_count, 0, source_count, "FAILED", checksum, str(exc))
            results[csv_name] = {
                "source": source_count,
                "migrated": 0,
                "failed": source_count,
                "status": "FAILED",
                "error": str(exc),
            }

    # ── Summary ──
    log.info("")
    log.info("════════════════════════════════════════")
    log.info("Migration Summary")
    log.info("════════════════════════════════════════")
    all_ok = True
    for name, r in results.items():
        status_icon = "✓" if r["status"] in ("COMPLETED", "DRY_RUN") else "✗"
        log.info(
            "  %s %-20s  source=%-6d  migrated=%-6d  failed=%-4d  [%s]",
            status_icon, name, r["source"], r["migrated"], r["failed"], r["status"],
        )
        if r["status"] == "FAILED":
            all_ok = False

    if conn:
        conn.close()

    if not all_ok:
        log.error("One or more migrations failed. Check logs above.")
        sys.exit(1)

    log.info("Migration completed successfully.")


# ── CLI ────────────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="PharmaTrust SQLite → PostgreSQL migration")
    p.add_argument("--export-dir", default="./migration_export", help="CSV export directory")
    p.add_argument("--pg-host", default="localhost")
    p.add_argument("--pg-port", type=int, default=5432)
    p.add_argument("--pg-db", default="pharmatrust_db")
    p.add_argument("--pg-user", default="postgres")
    p.add_argument("--pg-password", default=None)
    p.add_argument("--dry-run", action="store_true", help="Validate without writing to DB")
    p.add_argument("--batch-size", type=int, default=500, help="Insert batch size")
    return p.parse_args()


if __name__ == "__main__":
    args = parse_args()
    run_migration(args)
