# PharmaTrust - Migration Scripts

**Task 21.1** | Requirements: DR-001  
**Task 21.2** | Requirements: DR-002

## Overview

These scripts migrate existing PharmaTrust data from SQLite to PostgreSQL and upload local lab reports to AWS S3 with full integrity verification.

## Scripts

| Script | Purpose |
|--------|---------|
| `01_export_sqlite.sh` | Export all tables from SQLite to CSV files |
| `02_migrate_to_postgres.py` | Transform schema and import CSVs into PostgreSQL |
| `03_verify_integrity.py` | Verify data integrity post-migration |
| `04_migrate_lab_reports_s3.py` | Upload local lab reports to S3, hash and update DB |
| `run_migration.sh` | Master pipeline runner (Steps 1–3 always; Step 4 optional) |

## Prerequisites

```bash
# Python dependencies
pip install psycopg2-binary boto3

# SQLite CLI (for export step)
# Linux/macOS: usually pre-installed
# Windows: download from https://www.sqlite.org/download.html
```

## Quick Start

### Full migration pipeline (Steps 1–3)

```bash
# Linux/macOS
SQLITE_DB=./pharmatrust.db \
PG_HOST=localhost \
PG_PORT=5432 \
PG_DB=pharmatrust_db \
PG_USER=postgres \
PG_PASSWORD=yourpassword \
bash scripts/migration/run_migration.sh

# Windows (PowerShell)
$env:SQLITE_DB=".\pharmatrust.db"
$env:PG_HOST="localhost"
$env:PG_PORT="5432"
$env:PG_DB="pharmatrust_db"
$env:PG_USER="postgres"
$env:PG_PASSWORD="yourpassword"
bash scripts/migration/run_migration.sh
```

### Full pipeline including lab report S3 migration (Steps 1–4)

```bash
SQLITE_DB=./pharmatrust.db \
PG_HOST=localhost PG_DB=pharmatrust_db PG_USER=postgres PG_PASSWORD=yourpassword \
RUN_S3_MIGRATION=true \
LAB_REPORTS_DIR=./lab_reports \
S3_BUCKET_NAME=pharmatrust-lab-reports \
AWS_REGION=us-east-1 \
bash scripts/migration/run_migration.sh
```

### Dry run (validate without writing)

```bash
DRY_RUN=true bash scripts/migration/run_migration.sh

# Dry run including S3 step
DRY_RUN=true RUN_S3_MIGRATION=true bash scripts/migration/run_migration.sh
```

### Step-by-step

```bash
# Step 1: Export SQLite
bash scripts/migration/01_export_sqlite.sh ./pharmatrust.db ./migration_export

# Step 2: Migrate to PostgreSQL
python3 scripts/migration/02_migrate_to_postgres.py \
  --export-dir ./migration_export \
  --pg-host localhost \
  --pg-db pharmatrust_db \
  --pg-user postgres \
  --pg-password yourpassword

# Step 3: Verify integrity
python3 scripts/migration/03_verify_integrity.py \
  --export-dir ./migration_export \
  --pg-host localhost \
  --pg-db pharmatrust_db \
  --pg-user postgres \
  --pg-password yourpassword

# Step 4 (optional): Migrate lab reports to S3
python3 scripts/migration/04_migrate_lab_reports_s3.py \
  --lab-reports-dir ./lab_reports \
  --s3-bucket pharmatrust-lab-reports \
  --aws-region us-east-1 \
  --pg-host localhost \
  --pg-db pharmatrust_db \
  --pg-user postgres \
  --pg-password yourpassword
```

## Schema Transformations

The migration handles these SQLite → PostgreSQL differences:

| Concern | SQLite | PostgreSQL |
|---------|--------|-----------|
| UUIDs | Stored as TEXT | Native UUID type |
| Booleans | 0/1 integers | TRUE/FALSE |
| Timestamps | Various string formats | ISO-8601 TIMESTAMP |
| Enums | Any string | CHECK constraints enforced |
| Auto-increment | INTEGER PRIMARY KEY | UUID with uuid_generate_v4() |

### Role mapping (legacy → new)

| Legacy | PostgreSQL |
|--------|-----------|
| ADMIN | MANUFACTURER |
| USER | PHARMACIST |
| RETAILER | PHARMACIST |
| PATIENT | PHARMACIST |

### Batch status mapping

| Legacy | PostgreSQL |
|--------|-----------|
| DRAFT / CREATED | PROCESSING |
| APPROVED | ACTIVE |
| REJECTED / EXPIRED | RECALLED |

## Step 4: Lab Report S3 Migration

`04_migrate_lab_reports_s3.py` migrates local lab report files to AWS S3 (DR-002).

### What it does

1. Scans `--lab-reports-dir` for PDF files
2. Calculates SHA-256 hash for each file
3. Uploads to S3 with SSE-S3 encryption (`ServerSideEncryption: AES256`)
4. Matches each file to a batch record in PostgreSQL (by filename stem or batch number)
5. Updates `batches.lab_report_s3_key` and `batches.lab_report_hash` in the database
6. Moves successfully uploaded files to `--archive-dir` (default: `./lab_reports/archived/`)
7. Logs all operations and prints a summary

### File matching strategy

Files are matched to batch records in this order:
1. Filename stem appears in the existing `lab_report_s3_key` column
2. Filename stem matches `batch_number` (case-insensitive)

Unmatched files are still uploaded to `s3://{bucket}/lab-reports/unmatched/` for manual review.

### Idempotency

Files already migrated (matching `lab_report_hash` in DB) are skipped automatically.

### AWS credentials

Credentials are resolved in this order:
1. `--aws-access-key` / `--aws-secret-key` CLI flags
2. `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` environment variables
3. IAM instance role (recommended for EC2/ECS deployments)

### Environment variables for `run_migration.sh`

| Variable | Default | Description |
|----------|---------|-------------|
| `RUN_S3_MIGRATION` | `false` | Set to `true` to enable Step 4 |
| `LAB_REPORTS_DIR` | `./lab_reports` | Local directory with PDF files |
| `LAB_ARCHIVE_DIR` | `./lab_reports/archived` | Archive destination |
| `S3_BUCKET_NAME` | `pharmatrust-lab-reports` | Target S3 bucket |
| `S3_PREFIX` | `lab-reports` | S3 key prefix |
| `AWS_REGION` | `us-east-1` | AWS region |

## Integrity Checks

`03_verify_integrity.py` runs 7 check categories:

1. **Row counts** — PostgreSQL counts ≥ SQLite export counts
2. **Referential integrity** — All FK relationships intact
3. **Unique constraints** — No duplicate usernames, batch numbers, serial numbers
4. **NOT NULL constraints** — Critical fields populated
5. **Enum constraints** — All status/role values within allowed set
6. **Business rules** — expiry_date > manufacturing_date, scan counts ≥ 0
7. **Migration log** — `migration_log` table shows COMPLETED status

## Idempotency

All inserts use `ON CONFLICT (id) DO NOTHING` — safe to re-run without creating duplicates.

## Rollback

The migration does not delete source data. To rollback:

1. Stop the application
2. Drop and recreate the PostgreSQL database
3. Re-run Flyway migrations: `spring.flyway.enabled=true`
4. Restore from PostgreSQL backup if needed

## Flyway Integration

The `V2__migration_tracking.sql` Flyway script creates the `migration_log` table used to track migration progress. Enable Flyway before running the migration:

```properties
# application.properties
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
```
