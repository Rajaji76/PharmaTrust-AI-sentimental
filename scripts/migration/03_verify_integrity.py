#!/usr/bin/env python3
"""
PharmaTrust - Post-Migration Data Integrity Verification
Task 21.1: Verify data integrity post-migration
Requirements: DR-001

Usage:
    python3 03_verify_integrity.py [OPTIONS]

Options:
    --export-dir   Path to CSV export directory (default: ./migration_export)
    --pg-host      PostgreSQL host (default: localhost)
    --pg-port      PostgreSQL port (default: 5432)
    --pg-db        PostgreSQL database name (default: pharmatrust_db)
    --pg-user      PostgreSQL username (default: postgres)
    --pg-password  PostgreSQL password (default: from env PG_PASSWORD)
    --fail-fast    Exit immediately on first failure
"""

import argparse
import csv
import logging
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Tuple

try:
    import psycopg2
    import psycopg2.extras
    HAS_PSYCOPG2 = True
except ImportError:
    HAS_PSYCOPG2 = False

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(f"integrity_check_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"),
    ],
)
log = logging.getLogger(__name__)

# ── Verification checks ────────────────────────────────────────────────────────

class IntegrityChecker:
    def __init__(self, conn, export_dir: Path):
        self.conn = conn
        self.export_dir = export_dir
        self.failures: List[str] = []
        self.warnings: List[str] = []
        self.passed: List[str] = []

    def _query(self, sql: str, params=None):
        with self.conn.cursor() as cur:
            cur.execute(sql, params or ())
            return cur.fetchall()

    def _count(self, table: str, where: str = "") -> int:
        sql = f"SELECT COUNT(*) FROM {table}"
        if where:
            sql += f" WHERE {where}"
        return self._query(sql)[0][0]

    def _csv_count(self, name: str) -> int:
        path = self.export_dir / f"{name}.csv"
        if not path.exists() or path.stat().st_size == 0:
            return 0
        with open(path, newline="", encoding="utf-8") as f:
            return sum(1 for _ in csv.reader(f)) - 1  # subtract header

    def check_row_counts(self):
        """Verify PostgreSQL row counts are >= SQLite export counts."""
        log.info("── Check 1: Row count comparison ──")
        tables = [
            ("users", "users"),
            ("batches", "batches"),
            ("unit_items", "unit_items"),
            ("scan_logs", "scan_logs"),
            ("batch_approvals", "batch_approvals"),
            ("recall_events", "recall_events"),
            ("ownership_logs", "ownership_logs"),
            ("batch_jobs", "batch_jobs"),
        ]
        for csv_name, pg_table in tables:
            csv_count = self._csv_count(csv_name)
            pg_count = self._count(pg_table)
            if csv_count == 0:
                log.info("  %-20s  csv=%-6d  pg=%-6d  [SKIP - no source data]", pg_table, csv_count, pg_count)
                self.passed.append(f"row_count:{pg_table} (no source)")
            elif pg_count >= csv_count:
                log.info("  ✓ %-20s  csv=%-6d  pg=%-6d", pg_table, csv_count, pg_count)
                self.passed.append(f"row_count:{pg_table}")
            else:
                msg = f"Row count mismatch for {pg_table}: csv={csv_count}, pg={pg_count}"
                log.error("  ✗ %s", msg)
                self.failures.append(msg)

    def check_referential_integrity(self):
        """Verify FK relationships are intact."""
        log.info("── Check 2: Referential integrity ──")

        checks = [
            # (description, sql)
            (
                "batches.manufacturer_id → users.id",
                "SELECT COUNT(*) FROM batches b LEFT JOIN users u ON b.manufacturer_id = u.id WHERE u.id IS NULL",
            ),
            (
                "unit_items.batch_id → batches.id",
                "SELECT COUNT(*) FROM unit_items ui LEFT JOIN batches b ON ui.batch_id = b.id WHERE b.id IS NULL",
            ),
            (
                "unit_items.parent_unit_id → unit_items.id",
                "SELECT COUNT(*) FROM unit_items ui LEFT JOIN unit_items p ON ui.parent_unit_id = p.id WHERE ui.parent_unit_id IS NOT NULL AND p.id IS NULL",
            ),
            (
                "scan_logs.unit_id → unit_items.id",
                "SELECT COUNT(*) FROM scan_logs sl LEFT JOIN unit_items ui ON sl.unit_id = ui.id WHERE ui.id IS NULL",
            ),
            (
                "batch_approvals.batch_id → batches.id",
                "SELECT COUNT(*) FROM batch_approvals ba LEFT JOIN batches b ON ba.batch_id = b.id WHERE b.id IS NULL",
            ),
            (
                "recall_events.batch_id → batches.id",
                "SELECT COUNT(*) FROM recall_events re LEFT JOIN batches b ON re.batch_id = b.id WHERE b.id IS NULL",
            ),
            (
                "ownership_logs.unit_id → unit_items.id",
                "SELECT COUNT(*) FROM ownership_logs ol LEFT JOIN unit_items ui ON ol.unit_id = ui.id WHERE ui.id IS NULL",
            ),
        ]

        for desc, sql in checks:
            orphan_count = self._query(sql)[0][0]
            if orphan_count == 0:
                log.info("  ✓ %s", desc)
                self.passed.append(f"fk:{desc}")
            else:
                msg = f"FK violation: {desc} — {orphan_count} orphaned rows"
                log.error("  ✗ %s", msg)
                self.failures.append(msg)

    def check_unique_constraints(self):
        """Verify unique constraints are satisfied."""
        log.info("── Check 3: Unique constraints ──")

        checks = [
            ("users.username uniqueness",
             "SELECT username, COUNT(*) FROM users GROUP BY username HAVING COUNT(*) > 1"),
            ("batches.batch_number uniqueness",
             "SELECT batch_number, COUNT(*) FROM batches GROUP BY batch_number HAVING COUNT(*) > 1"),
            ("unit_items.serial_number uniqueness",
             "SELECT serial_number, COUNT(*) FROM unit_items GROUP BY serial_number HAVING COUNT(*) > 1"),
        ]

        for desc, sql in checks:
            duplicates = self._query(sql)
            if not duplicates:
                log.info("  ✓ %s", desc)
                self.passed.append(f"unique:{desc}")
            else:
                msg = f"Duplicate values found for {desc}: {duplicates[:5]}"
                log.error("  ✗ %s", msg)
                self.failures.append(msg)

    def check_not_null_constraints(self):
        """Verify critical NOT NULL fields are populated."""
        log.info("── Check 4: NOT NULL constraints ──")

        checks = [
            ("users.username not null",
             "SELECT COUNT(*) FROM users WHERE username IS NULL OR username = ''"),
            ("users.password_hash not null",
             "SELECT COUNT(*) FROM users WHERE password_hash IS NULL OR password_hash = ''"),
            ("batches.batch_number not null",
             "SELECT COUNT(*) FROM batches WHERE batch_number IS NULL OR batch_number = ''"),
            ("batches.medicine_name not null",
             "SELECT COUNT(*) FROM batches WHERE medicine_name IS NULL OR medicine_name = ''"),
            ("batches.manufacturing_date not null",
             "SELECT COUNT(*) FROM batches WHERE manufacturing_date IS NULL"),
            ("batches.expiry_date not null",
             "SELECT COUNT(*) FROM batches WHERE expiry_date IS NULL"),
            ("unit_items.serial_number not null",
             "SELECT COUNT(*) FROM unit_items WHERE serial_number IS NULL OR serial_number = ''"),
            ("unit_items.batch_id not null",
             "SELECT COUNT(*) FROM unit_items WHERE batch_id IS NULL"),
        ]

        for desc, sql in checks:
            null_count = self._query(sql)[0][0]
            if null_count == 0:
                log.info("  ✓ %s", desc)
                self.passed.append(f"notnull:{desc}")
            else:
                msg = f"NULL constraint violation: {desc} — {null_count} rows"
                log.error("  ✗ %s", msg)
                self.failures.append(msg)

    def check_enum_constraints(self):
        """Verify enum/check constraint values are valid."""
        log.info("── Check 5: Enum/CHECK constraints ──")

        valid_roles = "('MANUFACTURER','DISTRIBUTOR','PHARMACIST','REGULATOR','PRODUCTION_HEAD','QUALITY_CHECKER')"
        valid_batch_status = "('PROCESSING','PENDING_APPROVAL','ACTIVE','QUARANTINE','RECALLED','RECALLED_AUTO')"
        valid_unit_status = "('ACTIVE','TRANSFERRED','RECALLED','RECALLED_AUTO','EXPIRED')"
        valid_scan_result = "('VALID','INVALID','SUSPICIOUS','FLAGGED')"

        checks = [
            ("users.role valid values",
             f"SELECT COUNT(*) FROM users WHERE role NOT IN {valid_roles}"),
            ("batches.status valid values",
             f"SELECT COUNT(*) FROM batches WHERE status NOT IN {valid_batch_status}"),
            ("unit_items.status valid values",
             f"SELECT COUNT(*) FROM unit_items WHERE status NOT IN {valid_unit_status}"),
            ("scan_logs.scan_result valid values",
             f"SELECT COUNT(*) FROM scan_logs WHERE scan_result NOT IN {valid_scan_result}"),
        ]

        for desc, sql in checks:
            invalid_count = self._query(sql)[0][0]
            if invalid_count == 0:
                log.info("  ✓ %s", desc)
                self.passed.append(f"enum:{desc}")
            else:
                msg = f"Invalid enum value: {desc} — {invalid_count} rows"
                log.error("  ✗ %s", msg)
                self.failures.append(msg)

    def check_business_rules(self):
        """Verify pharmaceutical business rules."""
        log.info("── Check 6: Business rule validation ──")

        checks = [
            ("batch expiry_date > manufacturing_date",
             "SELECT COUNT(*) FROM batches WHERE expiry_date <= manufacturing_date"),
            ("unit scan_count >= 0",
             "SELECT COUNT(*) FROM unit_items WHERE scan_count < 0"),
            ("unit max_scan_limit >= 1",
             "SELECT COUNT(*) FROM unit_items WHERE max_scan_limit < 1"),
            ("scan anomaly_score in [0.0, 1.0]",
             "SELECT COUNT(*) FROM scan_logs WHERE anomaly_score < 0.0 OR anomaly_score > 1.0"),
        ]

        for desc, sql in checks:
            violation_count = self._query(sql)[0][0]
            if violation_count == 0:
                log.info("  ✓ %s", desc)
                self.passed.append(f"business:{desc}")
            else:
                msg = f"Business rule violation: {desc} — {violation_count} rows"
                log.warning("  ⚠ %s", msg)
                self.warnings.append(msg)

    def check_migration_log(self):
        """Verify migration_log table shows all entities completed."""
        log.info("── Check 7: Migration log status ──")
        rows = self._query(
            "SELECT entity_type, status, source_count, migrated_count, failed_count "
            "FROM migration_log ORDER BY entity_type"
        )
        if not rows:
            log.info("  No migration_log entries found (migration may not have used this table)")
            return

        for entity_type, status, source, migrated, failed in rows:
            if status == "COMPLETED" and failed == 0:
                log.info("  ✓ %-20s  source=%-6d  migrated=%-6d  failed=0", entity_type, source, migrated)
                self.passed.append(f"migration_log:{entity_type}")
            elif failed > 0:
                msg = f"Migration log shows failures for {entity_type}: {failed} failed rows"
                log.warning("  ⚠ %s", msg)
                self.warnings.append(msg)
            else:
                log.info("  ~ %-20s  status=%s", entity_type, status)

    def run_all(self, fail_fast: bool = False) -> bool:
        checks = [
            self.check_row_counts,
            self.check_referential_integrity,
            self.check_unique_constraints,
            self.check_not_null_constraints,
            self.check_enum_constraints,
            self.check_business_rules,
            self.check_migration_log,
        ]

        for check in checks:
            check()
            if fail_fast and self.failures:
                break

        log.info("")
        log.info("════════════════════════════════════════")
        log.info("Integrity Check Summary")
        log.info("════════════════════════════════════════")
        log.info("  Passed  : %d", len(self.passed))
        log.info("  Warnings: %d", len(self.warnings))
        log.info("  Failures: %d", len(self.failures))

        if self.warnings:
            log.info("")
            log.info("Warnings:")
            for w in self.warnings:
                log.warning("  ⚠ %s", w)

        if self.failures:
            log.info("")
            log.error("Failures:")
            for f in self.failures:
                log.error("  ✗ %s", f)
            return False

        log.info("")
        log.info("✓ All integrity checks passed.")
        return True


# ── CLI ────────────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="PharmaTrust post-migration integrity verification")
    p.add_argument("--export-dir", default="./migration_export")
    p.add_argument("--pg-host", default="localhost")
    p.add_argument("--pg-port", type=int, default=5432)
    p.add_argument("--pg-db", default="pharmatrust_db")
    p.add_argument("--pg-user", default="postgres")
    p.add_argument("--pg-password", default=None)
    p.add_argument("--fail-fast", action="store_true")
    return p.parse_args()


def main():
    args = parse_args()

    if not HAS_PSYCOPG2:
        log.error("psycopg2 not installed. Run: pip install psycopg2-binary")
        sys.exit(1)

    password = args.pg_password or os.environ.get("PG_PASSWORD", "")
    log.info("Connecting to PostgreSQL at %s:%s/%s", args.pg_host, args.pg_port, args.pg_db)
    conn = psycopg2.connect(
        host=args.pg_host,
        port=args.pg_port,
        dbname=args.pg_db,
        user=args.pg_user,
        password=password,
    )

    checker = IntegrityChecker(conn, Path(args.export_dir))
    success = checker.run_all(fail_fast=args.fail_fast)
    conn.close()

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
