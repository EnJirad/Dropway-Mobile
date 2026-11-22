# Dropway Database Schema Specification (Cloud & Local)

## 1. Cloud Database Schema (Neon PostgreSQL)

The central database uses PostgreSQL on Neon Serverless. It is fully normalized, supports multi-tenancy via `user_id`, and includes auditing.

```sql
-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30),
    role VARCHAR(20) DEFAULT 'user' CHECK (role IN ('user', 'driver', 'admin')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Folders Table
CREATE TABLE IF NOT EXISTS folders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color VARCHAR(10) DEFAULT '#3B82F6',
    icon VARCHAR(50) DEFAULT 'folder',
    is_default BOOLEAN DEFAULT FALSE,
    is_archived BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_folders_user ON folders(user_id);

-- 3. Houses / Addresses Master Table
CREATE TABLE IF NOT EXISTS houses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    house_number VARCHAR(50) NOT NULL,
    village VARCHAR(100),
    subdistrict VARCHAR(100),
    district VARCHAR(100),
    province VARCHAR(100),
    postal_code VARCHAR(10),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    phone_numbers TEXT[], -- Array of normalized phone numbers
    recipient_names TEXT[], -- Known recipients at this address
    notes TEXT,
    landmark TEXT,
    photo_urls TEXT[],
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version INT DEFAULT 1,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_houses_coords ON houses(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_houses_search ON houses(house_number, village, district);

-- 4. Folder Houses (Many-to-Many Association)
CREATE TABLE IF NOT EXISTS folder_houses (
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (folder_id, house_id)
);
CREATE INDEX IF NOT EXISTS idx_folder_houses_house ON folder_houses(house_id);

-- 5. User Navigation & Delivery Session
CREATE TABLE IF NOT EXISTS user_navigation_houses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
    folder_id UUID REFERENCES folders(id) ON DELETE SET NULL,
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'en_route', 'delivered', 'failed', 'cancelled')),
    delivery_order INT NOT NULL DEFAULT 0,
    parcel_number VARCHAR(100),
    recipient_name VARCHAR(100),
    phone_number VARCHAR(30),
    cod_amount NUMERIC(10, 2) DEFAULT 0.00,
    notes TEXT,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_nav_user_status ON user_navigation_houses(user_id, status);

-- 6. Folder Sharing & Permissions
CREATE TABLE IF NOT EXISTS shared_folders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_with_email VARCHAR(255),
    permission VARCHAR(20) DEFAULT 'read' CHECK (permission IN ('read', 'write', 'admin')),
    token_hash VARCHAR(64) UNIQUE, -- SHA-256 hash of share token
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Subscriptions & Entitlements
CREATE TABLE IF NOT EXISTS subscriptions (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    plan_id VARCHAR(50) NOT NULL DEFAULT 'free',
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'past_due', 'cancelled', 'expired')),
    starts_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    signature VARCHAR(255), -- Cryptographic verification hash
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. Sync Audit Log
CREATE TABLE IF NOT EXISTS sync_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    client_timestamp TIMESTAMP WITH TIME ZONE,
    server_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    payload JSONB
);
```

---

## 2. Local Mobile Database Schema (SQLite v2)

The Android local database is managed by `LocalHouseDatabase.kt`. Version 2 introduces complete support for Folders, Outbox Mutations, and Version Tracking.

```sql
-- 1. Local Houses Table
CREATE TABLE IF NOT EXISTS houses (
    id TEXT PRIMARY KEY,
    house_no TEXT NOT NULL,
    village TEXT DEFAULT '',
    subdistrict TEXT DEFAULT '',
    district TEXT DEFAULT '',
    province TEXT DEFAULT '',
    postal_code TEXT DEFAULT '',
    latitude REAL,
    longitude REAL,
    phone TEXT DEFAULT '',
    recipient_name TEXT DEFAULT '',
    notes TEXT DEFAULT '',
    landmark TEXT DEFAULT '',
    photo_path TEXT DEFAULT '',
    version INTEGER DEFAULT 1,
    sync_status TEXT DEFAULT 'SYNCED', -- 'SYNCED', 'PENDING_INSERT', 'PENDING_UPDATE', 'PENDING_DELETE'
    is_deleted INTEGER DEFAULT 0,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);
CREATE INDEX IF NOT EXISTS idx_local_houses_search ON houses(house_no, village, district);
CREATE INDEX IF NOT EXISTS idx_local_houses_sync ON houses(sync_status);

-- 2. Local Folders Table
CREATE TABLE IF NOT EXISTS folders (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    color TEXT DEFAULT '#3B82F6',
    icon TEXT DEFAULT 'folder',
    is_default INTEGER DEFAULT 0,
    is_archived INTEGER DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    sync_status TEXT DEFAULT 'SYNCED',
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

-- 3. Local Folder Houses Association
CREATE TABLE IF NOT EXISTS folder_houses (
    folder_id TEXT NOT NULL,
    house_id TEXT NOT NULL,
    added_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    sync_status TEXT DEFAULT 'SYNCED',
    PRIMARY KEY (folder_id, house_id)
);

-- 4. Local User Navigation Houses
CREATE TABLE IF NOT EXISTS user_navigation_houses (
    id TEXT PRIMARY KEY,
    house_id TEXT NOT NULL,
    folder_id TEXT DEFAULT '',
    status TEXT DEFAULT 'pending', -- 'pending', 'en_route', 'delivered', 'failed'
    delivery_order INTEGER DEFAULT 0,
    parcel_number TEXT DEFAULT '',
    recipient_name TEXT DEFAULT '',
    phone_number TEXT DEFAULT '',
    cod_amount REAL DEFAULT 0.0,
    notes TEXT DEFAULT '',
    delivered_at INTEGER DEFAULT 0,
    sync_status TEXT DEFAULT 'SYNCED',
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);
CREATE INDEX IF NOT EXISTS idx_local_nav_status ON user_navigation_houses(status, delivery_order);

-- 5. Outbox Mutations Queue
CREATE TABLE IF NOT EXISTS outbox_mutations (
    id TEXT PRIMARY KEY, -- UUIDv4 / Idempotency Key
    entity_type TEXT NOT NULL, -- 'HOUSE', 'FOLDER', 'NAVIGATION'
    entity_id TEXT NOT NULL,
    action TEXT NOT NULL, -- 'CREATE', 'UPDATE', 'DELETE'
    payload TEXT NOT NULL, -- JSON string of entity data
    client_timestamp INTEGER NOT NULL,
    retry_count INTEGER DEFAULT 0,
    last_error TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING' -- 'PENDING', 'PROCESSING', 'FAILED'
);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_mutations(status, retry_count);

-- 6. User Profile & Local Session
CREATE TABLE IF NOT EXISTS user_profile (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    display_name TEXT NOT NULL,
    auth_token TEXT NOT NULL,
    plan_type TEXT DEFAULT 'free',
    last_synced_at INTEGER DEFAULT 0
);
```

---

## 3. Migration Strategy (v1 to v2)

When upgrading `LocalHouseDatabase` from schema version 1 to 2:
1. Preserve existing rows in `houses` and `user_navigation_houses`.
2. Execute `ALTER TABLE houses ADD COLUMN version INTEGER DEFAULT 1;` if not present.
3. Execute `ALTER TABLE houses ADD COLUMN sync_status TEXT DEFAULT 'SYNCED';` if not present.
4. Execute `ALTER TABLE houses ADD COLUMN is_deleted INTEGER DEFAULT 0;` if not present.
5. Create new tables: `folders`, `folder_houses`, `outbox_mutations`, `user_profile`.
6. Insert default folder: `"mae_sot_default"` ("Mae Sot Deliveries") and link all existing houses to it.
