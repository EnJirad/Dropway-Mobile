# Dropway Architecture Decision Records (ADRs)

## ADR-001: Cloud-First Architecture with Local SQLite Offline Outbox Engine

### Context
Dropway couriers and drivers operate in environments where mobile data connectivity is inconsistent (rural delivery areas, underground parking, warehouse complexes). However, multi-user fleet dispatching and cloud synchronization require a centralized database.

### Decision
We adopt a **Cloud-First + Offline-Capable Architecture**:
1. The cloud database (PostgreSQL on Neon) is the authoritative source of truth for all synchronized records.
2. The local mobile SQLite database is a full operational replica providing $< 10$ms response times for all reads and writes.
3. Local writes generate durable records in an `outbox_mutations` table, which are uploaded to the cloud when online.

### Consequences
- **Positive:** Zero UI lag, 100% offline workflow capability, resilient against network drops.
- **Negative:** Requires conflict resolution rules and outbox retry state handling.

---

## ADR-002: Modular Clean Architecture (Domain, Data, UI Layers)

### Context
The previous mobile codebase mixed database queries directly with UI state in Compose components. This made testing difficult and prevented clean remote sync integration.

### Decision
Structure the codebase into three distinct layers:
1. **Domain Layer:** Pure Kotlin models (`House`, `Folder`, `NavigationHouse`, `User`) and repository interfaces. No Android SDK or database dependencies.
2. **Data Layer:** Local SQLite (`LocalHouseDatabase`), Remote API client (`DropwayApiClient`), Keystore encryption (`VaultCryptoManager`), and repository implementations.
3. **Presentation / UI Layer:** Jetpack Compose screens, Material 3 theme, ViewModels with `StateFlow`.

### Consequences
- **Positive:** Highly testable, clean separation of concerns, components can be refactored independently.
- **Negative:** Requires mapping between database entities, domain models, and API DTOs.

---

## ADR-003: SQLite Migration Strategy (v1 to v2)

### Context
The existing database contains 4,530 historical records in Mae Sot. A naive table recreation (`DROP TABLE IF EXISTS`) would cause catastrophic data loss.

### Decision
1. SQLite migration checks existing columns and executes non-destructive `ALTER TABLE` statements.
2. Introduces new tables: `folders`, `folder_houses`, `outbox_mutations`, `user_profile`.
3. Creates a default folder `"mae_sot_default"` ("Mae Sot Deliveries") and links all 4,530 existing houses to it.

### Consequences
- **Positive:** Zero data loss, seamless upgrade for existing users, full backward compatibility.
- **Negative:** Migration logic must handle partial schemas gracefully.

---

## ADR-004: Vector Icons Only (Zero Emoji Policy)

### Context
Unicode emojis render inconsistently across different Android OS versions and device manufacturers (Samsung, Xiaomi, Google Pixel), and can look unprofessional in an enterprise logistics tool.

### Decision
Strict **Zero Emoji** policy across all UI screens, dialogs, badges, and notification strings. All icons must use standard Android Material vector icons (`androidx.compose.material.icons`) or custom vector drawables.

### Consequences
- **Positive:** Consistent visual presentation across all Android devices, professional enterprise aesthetic.
- **Negative:** Requires creating or mapping vector icons for status badges and tags.

---

## ADR-005: Hardware-Backed Cryptography via Android KeyStore

### Context
Sensitive tokens and QR share payloads require tamper protection and local encryption.

### Decision
Use Android KeyStore system (`AndroidKeyStore`) with AES-256-GCM encryption for stored credentials and HMAC-SHA256 for QR code signing. Keys never leave the secure hardware enclave (TEE/StrongBox).

### Consequences
- **Positive:** Military-grade data-at-rest security, non-exportable private keys.
- **Negative:** Requires Android 9+ (API 28+) as minSdk (already configured in `build.gradle.kts`).
