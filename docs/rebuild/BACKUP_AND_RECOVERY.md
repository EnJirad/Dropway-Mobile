# Dropway Backup & Disaster Recovery Specification

## 1. Objectives & Scope
Courier address databases represent high-value institutional knowledge built through thousands of field deliveries. Loss of data disrupts daily delivery operations and impacts driver income.
This document defines disaster recovery procedures across two primary domains:
1. **Client-Side Local SQLite Backup & Recovery:** Export/Import of local database archives, device replacement migrations, and crash recovery.
2. **Cloud PostgreSQL Automated Backups:** Neon serverless point-in-time recovery (PITR) and automated daily snapshots.

---

## 2. Client-Side Backup & Export

### Formats Supported
1. **Encrypted Archive (`.dropway`):** Full database export encrypted with user's vault key or password-derived key (PBKDF2 + AES-256-GCM). Contains houses, folders, custom landmarks, and delivery history.
2. **Standard CSV / JSON Export:** Unencrypted plain text export for Excel / external logistics integration, excluding sensitive security tokens.

### Export Workflow
- Driver navigates to **Settings > Data Management > Backup Data**.
- App reads local tables within a read-only transaction.
- Data is serialized into JSON with a cryptographic checksum (SHA-256).
- Android System File Picker (`ACTION_CREATE_DOCUMENT`) allows user to save to Google Drive, SD Card, or local Downloads.

### Recovery / Import Workflow
- App opens archive file via `ContentResolver`.
- Verifies SHA-256 checksum and schema version.
- Displays summary: `Found 4,530 Houses, 12 Folders`.
- Options:
  - **Replace All:** Wipes existing local tables and restores fresh.
  - **Merge / Deduplicate:** Matches on `house_no` and coordinates; updates newer records and appends missing entries.

---

## 3. Cloud Database Resilience (Neon PostgreSQL)

1. **Continuous WAL Archiving & Point-In-Time Recovery (PITR):** Neon serverless stores write-ahead logs in cloud object storage, allowing restoration to any second within the retention window (7–30 days).
2. **Automated Schema Backups:** Nightly dumps of table schemas and row data to an isolated cloud storage bucket.
3. **Database Health Checks:** Periodic verification queries checking table constraints, foreign key integrity, and index health.
