#!/usr/bin/env python3
"""
PharmaTrust - Lab Report Migration to AWS S3
Task 21.2: Migrate existing local lab reports to S3 with integrity hashing
Requirements: DR-002

Usage:
    python3 04_migrate_lab_reports_s3.py [OPTIONS]

Options:
    --lab-reports-dir  Local directory containing lab report files (default: ./lab_reports)
    --s3-bucket        S3 bucket name (default: pharmatrust-lab-reports)
    --s3-prefix        S3 key prefix (default: lab-reports)
    --aws-region       AWS region (default: us-east-1)
    --pg-host          PostgreSQL host (default: localhost)
    --pg-port          PostgreSQL port (default: 5432)
    --pg-db            PostgreSQL database name (default: pharmatrust_db)
    --pg-user          PostgreSQL username (default: postgres)
    --pg-password      PostgreSQL password (default: from env PG_PASSWORD)
    --archive-dir      Directory to move files after successful upload (default: ./lab_reports/archived)
    --dry-run          Validate and log without uploading or modifying DB
    --file-extensions  Comma-separated list of extensions to process (default: .pdf,.PDF)
    --batch-size       DB update batch size (default: 100)
"""

import argparse
import hashlib
import logging
import os
import shutil
import sys
import uuid
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ── Optional dependencies ──────────────────────────────────────────────────────
try:
    import boto3
    from botocore.exceptions import BotoCoreError, ClientError
    HAS_BOTO3 = True
except ImportError:
    HAS_BOTO3 = False

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
        logging.FileHandler(
            f"lab_report_migration_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
        ),
    ],
)
log = logging.getLogger(__name__)


# ── Hashing ────────────────────────────────────────────────────────────────────

def sha256_file(path: Path) -> str:
    """Calculate SHA-256 hash of a file, reading in 64 KB chunks."""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


# ── S3 helpers ─────────────────────────────────────────────────────────────────

def build_s3_client(region: str, aws_access_key: Optional[str], aws_secret_key: Optional[str]):
    """Build a boto3 S3 client. Falls back to IAM role / env credentials if keys not provided."""
    kwargs = {"region_name": region}
    if aws_access_key and aws_secret_key:
        kwargs["aws_access_key_id"] = aws_access_key
        kwargs["aws_secret_access_key"] = aws_secret_key
    return boto3.client("s3", **kwargs)


def upload_to_s3(
    s3_client,
    local_path: Path,
    bucket: str,
    s3_key: str,
    file_hash: str,
    batch_id: Optional[str],
) -> bool:
    """
    Upload a file to S3 with SSE-S3 encryption and metadata.
    Returns True on success, False on failure.
    """
    extra_args = {
        "ServerSideEncryption": "AES256",  # SSE-S3 — NFR-007
        "ContentType": "application/pdf",
        "Metadata": {
            "file-hash": file_hash,
            "original-filename": local_path.name,
            "upload-timestamp": datetime.utcnow().isoformat(),
        },
    }
    if batch_id:
        extra_args["Metadata"]["batch-id"] = batch_id

    try:
        s3_client.upload_file(
            str(local_path),
            bucket,
            s3_key,
            ExtraArgs=extra_args,
        )
        return True
    except (BotoCoreError, ClientError) as exc:
        log.error("S3 upload failed for %s → s3://%s/%s: %s", local_path.name, bucket, s3_key, exc)
        return False


# ── Database helpers ───────────────────────────────────────────────────────────

def get_pg_connection(args):
    if not HAS_PSYCOPG2:
        raise RuntimeError("psycopg2 not installed. Run: pip install psycopg2-binary")
    password = args.pg_password or os.environ.get("PG_PASSWORD", "")
    return psycopg2.connect(
        host=args.pg_host,
        port=args.pg_port,
        dbname=args.pg_db,
        user=args.pg_user,
        password=password,
    )


def find_batch_by_filename(conn, filename: str) -> Optional[Dict]:
    """
    Try to match a lab report file to a batch record.
    Matching strategy (in order):
      1. Exact match on lab_report_s3_key containing the filename stem
      2. Batch number embedded in the filename (e.g. BATCH001_report.pdf)
    Returns a dict with 'id', 'batch_number', 'lab_report_s3_key', 'lab_report_hash' or None.
    """
    stem = Path(filename).stem
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        # Strategy 1: filename stem appears in existing s3 key
        cur.execute(
            "SELECT id, batch_number, lab_report_s3_key, lab_report_hash "
            "FROM batches WHERE lab_report_s3_key ILIKE %s LIMIT 1",
            (f"%{stem}%",),
        )
        row = cur.fetchone()
        if row:
            return dict(row)

        # Strategy 2: filename stem matches batch_number
        cur.execute(
            "SELECT id, batch_number, lab_report_s3_key, lab_report_hash "
            "FROM batches WHERE batch_number = %s LIMIT 1",
            (stem.upper(),),
        )
        row = cur.fetchone()
        if row:
            return dict(row)

    return None


def update_batch_s3_info(
    conn,
    batch_id: str,
    s3_key: str,
    file_hash: str,
    dry_run: bool,
) -> bool:
    """Update batches table with S3 key and SHA-256 hash."""
    if dry_run:
        log.info("  [DRY RUN] Would UPDATE batches SET lab_report_s3_key='%s', lab_report_hash='%s' WHERE id='%s'",
                 s3_key, file_hash, batch_id)
        return True
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE batches SET lab_report_s3_key = %s, lab_report_hash = %s WHERE id = %s",
                (s3_key, file_hash, batch_id),
            )
        conn.commit()
        return True
    except Exception as exc:
        log.error("DB update failed for batch %s: %s", batch_id, exc)
        conn.rollback()
        return False


def log_migration_result(
    conn,
    filename: str,
    s3_key: Optional[str],
    file_hash: str,
    batch_id: Optional[str],
    status: str,
    error: Optional[str],
    dry_run: bool,
):
    """Insert a row into lab_report_migration_log if the table exists."""
    if dry_run:
        return
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO lab_report_migration_log
                    (filename, s3_key, file_hash, batch_id, status, migrated_at, error_details)
                VALUES (%s, %s, %s, %s::uuid, %s, NOW(), %s)
                ON CONFLICT DO NOTHING
                """,
                (filename, s3_key, file_hash, batch_id, status, error),
            )
        conn.commit()
    except Exception:
        # Table may not exist — non-fatal, just skip logging
        conn.rollback()


# ── Archive helper ─────────────────────────────────────────────────────────────

def archive_file(local_path: Path, archive_dir: Path, dry_run: bool) -> bool:
    """Move a successfully uploaded file to the archive directory."""
    if dry_run:
        log.info("  [DRY RUN] Would archive %s → %s/", local_path.name, archive_dir)
        return True
    try:
        archive_dir.mkdir(parents=True, exist_ok=True)
        dest = archive_dir / local_path.name
        # Avoid overwriting if a file with the same name already exists
        if dest.exists():
            dest = archive_dir / f"{local_path.stem}_{uuid.uuid4().hex[:8]}{local_path.suffix}"
        shutil.move(str(local_path), str(dest))
        log.info("  Archived: %s → %s", local_path.name, dest)
        return True
    except OSError as exc:
        log.warning("  Archive failed for %s: %s (file left in place)", local_path.name, exc)
        return False


# ── Core migration logic ───────────────────────────────────────────────────────

class LabReportMigrator:
    def __init__(self, args):
        self.args = args
        self.lab_dir = Path(args.lab_reports_dir)
        self.archive_dir = Path(args.archive_dir)
        self.extensions = {e.strip() for e in args.file_extensions.split(",")}
        self.dry_run = args.dry_run

        self.stats = {
            "total": 0,
            "uploaded": 0,
            "db_updated": 0,
            "archived": 0,
            "skipped": 0,
            "failed": 0,
            "unmatched": 0,
        }

        self.s3_client = None
        self.conn = None

    def setup(self):
        if not self.lab_dir.exists():
            log.error("Lab reports directory not found: %s", self.lab_dir)
            sys.exit(1)

        if not self.dry_run:
            if not HAS_BOTO3:
                log.error("boto3 not installed. Run: pip install boto3")
                sys.exit(1)
            if not HAS_PSYCOPG2:
                log.error("psycopg2 not installed. Run: pip install psycopg2-binary")
                sys.exit(1)

            aws_key = self.args.aws_access_key or os.environ.get("AWS_ACCESS_KEY_ID")
            aws_secret = self.args.aws_secret_key or os.environ.get("AWS_SECRET_ACCESS_KEY")
            self.s3_client = build_s3_client(self.args.aws_region, aws_key, aws_secret)
            log.info("S3 client initialised (region=%s, bucket=%s)", self.args.aws_region, self.args.s3_bucket)

            log.info("Connecting to PostgreSQL at %s:%s/%s", self.args.pg_host, self.args.pg_port, self.args.pg_db)
            self.conn = get_pg_connection(self.args)
            log.info("PostgreSQL connection established.")

    def collect_files(self) -> List[Path]:
        files = [
            f for f in self.lab_dir.iterdir()
            if f.is_file() and f.suffix in self.extensions
        ]
        files.sort(key=lambda p: p.name)
        return files

    def migrate_file(self, local_path: Path) -> str:
        """
        Process a single lab report file.
        Returns one of: 'uploaded', 'skipped', 'failed', 'unmatched'.
        """
        filename = local_path.name
        log.info("Processing: %s (%.1f KB)", filename, local_path.stat().st_size / 1024)

        # 1. Calculate SHA-256 hash
        file_hash = sha256_file(local_path)
        log.info("  SHA-256: %s", file_hash)

        # 2. Try to match to a batch record
        batch_info = None
        if self.conn:
            batch_info = find_batch_by_filename(self.conn, filename)

        if batch_info:
            batch_id = str(batch_info["id"])
            batch_number = batch_info["batch_number"]
            log.info("  Matched batch: %s (id=%s)", batch_number, batch_id)

            # Skip if already migrated with the same hash
            if batch_info.get("lab_report_s3_key") and batch_info.get("lab_report_hash") == file_hash:
                log.info("  Already migrated with matching hash — skipping.")
                return "skipped"
        else:
            batch_id = None
            batch_number = "UNMATCHED"
            log.warning("  No matching batch found for file: %s", filename)

        # 3. Build S3 key: lab-reports/{batch_id_or_unmatched}/{filename}
        s3_folder = batch_id if batch_id else "unmatched"
        s3_key = f"{self.args.s3_prefix}/{s3_folder}/{filename}"

        # 4. Upload to S3
        if self.dry_run:
            log.info("  [DRY RUN] Would upload → s3://%s/%s", self.args.s3_bucket, s3_key)
            upload_ok = True
        else:
            log.info("  Uploading → s3://%s/%s", self.args.s3_bucket, s3_key)
            upload_ok = upload_to_s3(
                self.s3_client, local_path, self.args.s3_bucket, s3_key, file_hash, batch_id
            )

        if not upload_ok:
            if self.conn:
                log_migration_result(self.conn, filename, None, file_hash, batch_id, "FAILED",
                                     "S3 upload error", self.dry_run)
            return "failed"

        log.info("  Upload successful.")

        # 5. Update database with S3 key and hash
        db_ok = True
        if batch_id and self.conn:
            db_ok = update_batch_s3_info(self.conn, batch_id, s3_key, file_hash, self.dry_run)
            if db_ok:
                log.info("  DB updated: lab_report_s3_key and lab_report_hash set.")
            else:
                log.error("  DB update failed — file uploaded but DB not updated.")

        # 6. Log migration result
        if self.conn:
            status = "COMPLETED" if db_ok else "UPLOAD_ONLY"
            log_migration_result(self.conn, filename, s3_key, file_hash, batch_id, status, None, self.dry_run)

        # 7. Archive local file after successful upload
        if upload_ok:
            archive_file(local_path, self.archive_dir, self.dry_run)
            self.stats["archived"] += 1

        if not batch_id:
            return "unmatched"
        return "uploaded"

    def run(self):
        self.setup()

        files = self.collect_files()
        self.stats["total"] = len(files)

        log.info("")
        log.info("╔══════════════════════════════════════════════════════╗")
        log.info("║   PharmaTrust Lab Report → S3 Migration              ║")
        log.info("╚══════════════════════════════════════════════════════╝")
        log.info("Lab reports dir : %s", self.lab_dir)
        log.info("S3 bucket       : %s", self.args.s3_bucket)
        log.info("S3 prefix       : %s", self.args.s3_prefix)
        log.info("Archive dir     : %s", self.archive_dir)
        log.info("Dry run         : %s", self.dry_run)
        log.info("Files found     : %d", self.stats["total"])
        log.info("")

        if self.stats["total"] == 0:
            log.warning("No lab report files found in %s with extensions %s",
                        self.lab_dir, self.extensions)

        for local_path in files:
            result = self.migrate_file(local_path)
            if result == "uploaded":
                self.stats["uploaded"] += 1
                self.stats["db_updated"] += 1
            elif result == "skipped":
                self.stats["skipped"] += 1
            elif result == "failed":
                self.stats["failed"] += 1
            elif result == "unmatched":
                self.stats["unmatched"] += 1
                self.stats["uploaded"] += 1  # still uploaded to unmatched/

        self._print_summary()

        if self.conn:
            self.conn.close()

        return self.stats["failed"] == 0

    def _print_summary(self):
        log.info("")
        log.info("════════════════════════════════════════")
        log.info("Lab Report Migration Summary")
        log.info("════════════════════════════════════════")
        log.info("  Total files    : %d", self.stats["total"])
        log.info("  Uploaded to S3 : %d", self.stats["uploaded"])
        log.info("  DB updated     : %d", self.stats["db_updated"])
        log.info("  Archived       : %d", self.stats["archived"])
        log.info("  Skipped        : %d", self.stats["skipped"])
        log.info("  Unmatched      : %d", self.stats["unmatched"])
        log.info("  Failed         : %d", self.stats["failed"])
        if self.stats["failed"] == 0:
            log.info("")
            log.info("✓ Lab report migration completed successfully.")
        else:
            log.error("")
            log.error("✗ %d file(s) failed to migrate. Check logs above.", self.stats["failed"])


# ── CLI ────────────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(
        description="PharmaTrust: Migrate local lab reports to AWS S3",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    # File system
    p.add_argument("--lab-reports-dir", default="./lab_reports",
                   help="Local directory containing lab report files")
    p.add_argument("--archive-dir", default="./lab_reports/archived",
                   help="Directory to move files after successful upload")
    p.add_argument("--file-extensions", default=".pdf,.PDF",
                   help="Comma-separated file extensions to process")
    # S3
    p.add_argument("--s3-bucket", default=os.environ.get("S3_BUCKET_NAME", "pharmatrust-lab-reports"),
                   help="Target S3 bucket name")
    p.add_argument("--s3-prefix", default="lab-reports",
                   help="S3 key prefix for uploaded files")
    p.add_argument("--aws-region", default=os.environ.get("AWS_REGION", "us-east-1"),
                   help="AWS region")
    p.add_argument("--aws-access-key", default=None,
                   help="AWS access key ID (falls back to env AWS_ACCESS_KEY_ID or IAM role)")
    p.add_argument("--aws-secret-key", default=None,
                   help="AWS secret access key (falls back to env AWS_SECRET_ACCESS_KEY or IAM role)")
    # PostgreSQL
    p.add_argument("--pg-host", default=os.environ.get("PG_HOST", "localhost"))
    p.add_argument("--pg-port", type=int, default=int(os.environ.get("PG_PORT", "5432")))
    p.add_argument("--pg-db", default=os.environ.get("PG_DB", "pharmatrust_db"))
    p.add_argument("--pg-user", default=os.environ.get("PG_USER", "postgres"))
    p.add_argument("--pg-password", default=None,
                   help="PostgreSQL password (falls back to env PG_PASSWORD)")
    p.add_argument("--batch-size", type=int, default=100,
                   help="DB update batch size")
    # Behaviour
    p.add_argument("--dry-run", action="store_true",
                   help="Validate and log without uploading or modifying DB")
    return p.parse_args()


def main():
    args = parse_args()
    migrator = LabReportMigrator(args)
    success = migrator.run()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
