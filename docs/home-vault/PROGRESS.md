# DROPWAY MOBILE — HOME VAULT PROGRESS TRACKER

This document tracks execution progress, verified criteria, and open tasks for the Home Vault rebuild.

---

## Overall Status Dashboard

- **Phase 0 — Investigation & Master Plan:** ✅ COMPLETED
- **Part 1 — Responsive Layout & Scroll Performance:** ✅ COMPLETED
- **Part 2 — Home Vault UI & Interactions:** ✅ COMPLETED
- **Part 3 — Home Edit Form & Map Picker:** ✅ COMPLETED
- **Part 4 — Local-First Home Vault Storage:** ✅ COMPLETED
- **Part 5 — Ownership, Creator Identity & Shared Data Model:** ✅ COMPLETED
- **Part 6 — Subscription-Based Secure Sharing:** ✅ COMPLETED
- **Part 7 — QA, Regression, Security & Release:** ✅ COMPLETED
- **Dropway 2.0 — Offline-First Secure Vault, .dwv Sharing & 16-Map Provider Engine:** ✅ COMPLETED

---

## Dropway Mobile 2.0 Implementation Checklist

- [x] **Complete Elimination of Neon Database:**
  - [x] Removed `NeonClient.kt` and all remote PostgreSQL network dependencies.
  - [x] Expanded local SQLite database to manage `navigation_stops`, `scanned_parcels`, and `subscription_licenses`.
  - [x] All CRUD operations in Navigation, Scan, Houses, and Settings operate 100% offline.
- [x] **Database Security & Anti-Extraction Protection:**
  - [x] Set `android:allowBackup="false"` in `AndroidManifest.xml` to prevent adb backup dumping.
  - [x] Isolated database in internal private app storage.
  - [x] Hardware-backed `AndroidKeyStore` AES-256-GCM encryption in `VaultCryptoManager.kt`.
- [x] **Proprietary Encrypted Sharing (`.dwv` - Dropway Vault):**
  - [x] Replaced plain JSON/CSV export with sealed binary `.dwv` container format (`DWVAULT02`).
  - [x] Compressed with ZLIB, encrypted with AES-256-GCM, and signed with SHA-256 checksums.
  - [x] Embedded creator attribution (`creatorId`, `creatorName`, `vaultTitle`, `recordCount`).
- [x] **Subscription Monetization & Creator Attribution:**
  - [x] Delivery drivers own and author their house pins and delivery routes.
  - [x] Non-subscribing recipients receive a subscription paywall when importing `.dwv` files.
  - [x] Offline licensing engine with signed activation codes (`DW-PRO-...`).
  - [x] Expired subscriptions lock shared houses without deleting personal user records.
- [x] **16-Provider Multi-Layer Map Engine:**
  - [x] Default map set to **Google Maps (ดาวเทียม)** (`google_satellite`) per user specification.
  - [x] Registered all 16 providers in `MapProviderRegistry.kt` (Google Satellite, Hybrid, Road, Terrain, Traffic, OSM, ESRI Suite, OpenTopoMap, Wikimedia, IGN France).
  - [x] Interactive Leaflet layer switching in `MapPickerDialog.kt` with category-based picker dialog.
- [x] **Build & Release:**
  - [x] Debug APK assembled successfully (`app-debug.apk` ~29.5 MB).
  - [x] Git committed and pushed to remote origin on `main`.

---

## Detailed Checklists

### Phase 0: Investigation & Master Plan
- [x] Inspect source code and layout hierarchy (`MainActivity.kt`, `HousesScreen.kt`, `CommonDialogs.kt`).
- [x] Identify root causes of top spacing bug (double Scaffold insets) and list lag (un-debounced search, regex re-creation, UI-thread district extraction).
- [x] Analyze 4,530-record real dataset from `.ai/houses_rows.csv`.
- [x] Author comprehensive `docs/home-vault/MASTER_PLAN.md`.
- [x] Author persistent checklist `docs/home-vault/PROGRESS.md`.

---

### Part 1: Responsive Layout & Scroll Performance
- [x] **1.1 Screen Height & System Insets:**
  - [x] Remove double status bar inset between `MainActivity` and `HousesScreen`.
  - [x] Adjust `contentPadding` in `LazyColumn` to eliminate excess 80dp bottom padding.
  - [x] Ensure edge-to-edge layout fits all Android screen ratios without clipping or hiding system status.
- [x] **1.2 Large Dataset Performance:**
  - [x] Pre-compile all regex patterns statically in `SearchMatcher` (`HOUSE_NO_REGEX`, `DISTRICT_REGEX`, `MOO_SPLIT_REGEX`, etc.).
  - [x] Move `districts` extraction off the main UI thread to `Dispatchers.Default`.
  - [x] Implement search query debouncing (250ms) to prevent UI thread starvation during typing.
  - [x] Optimize `HouseCard` parameters and avoid regex compilation inside item composition (`SearchMatcher.extractHouseNo`).
  - [x] Remove emoji `📝` from `HouseCard` and replace with professional vector icon.
- [x] **1.3 Verification & Testing:**
  - [x] Verify smooth scrolling and search responsiveness with 4,530 items (~4.16x faster regex throughput).
  - [x] Assemble debug APK and verify compilation (`BUILD SUCCESSFUL in 4m 15s`).

---

### Part 2: Home Vault UI & Interactions
- [x] **2.1 Add Home Button:**
  - [x] Move Add Home action to a compact toolbar button aligned to the far right.
  - [x] Lucide/Material plus icon with label "เพิ่มบ้าน".
  - [x] Remove oversized floating action button if redundant.
- [x] **2.2 Row Action Order:**
  - [x] Standardize action layout: Card Content -> Edit Button -> Delete Button (Edit precedes Delete).
  - [x] Icon-only controls with accessible descriptions.
- [x] **2.3 Delete Confirmation Dialog:**
  - [x] Dialog explicitly identifies house full name or address to be deleted.
  - [x] Cancel and Confirm Delete buttons; zero one-tap accidental deletions.

---

### Part 3: Home Edit Form & Map Picker
- [x] **3.1 Required Fields:**
  - [x] Unified Full Name field.
  - [x] Phone field with normalization.
  - [x] Single Coordinate field (`"lat,lng"`) with range validation via `CoordinateHelper`.
  - [x] Map Pin button on the right of coordinate field.
  - [x] Free-form multi-line notes/observations field.
  - [x] Remove parcel quantity field from edit form.
- [x] **3.2 Interactive Map Picker:**
  - [x] Center-pinned interactive map (user moves map under pin).
  - [x] Standard and Satellite map layers toggle (OpenStreetMap + Esri World Imagery).
  - [x] Current GPS location button with runtime permission handling.
  - [x] Apply selected coordinates back to the form.

---

### Part 4: Local-First Home Vault Storage
- [x] **4.1 Local Database Engine:**
  - [x] Structured local storage (`LocalHouseDatabase` SQLite) with schema versioning.
  - [x] Fast indexed lookups by phone, address, and name.
  - [x] Pre-seed 4,530 houses from bundled `assets/houses_seed.csv` into local storage.
- [x] **4.2 Offline Operation:**
  - [x] Full functionality without network connection or Neon credentials.
- [x] **4.3 Backup & Export/Import:**
  - [x] JSON backup file export via Android FileProvider Intent share.
  - [x] Backup file import with schema validation and duplicate detection.

---

### Part 5: Home Ownership & Creator Identity
- [x] Immutable creator ID, creation timestamp, and record version in schema.
- [x] Clear distinction between personal user records and shared records (`source_type = 'personal' | 'shared'`).
- [x] Soft blue badge identifying shared houses and creator name in UI.

---

### Part 6: Subscription-Based Secure Sharing
- [x] Configurable pricing model via `SubscriptionConfig` (29 THB/mo).
- [x] Cryptographic SHA-256 signature verification preventing tamper on shared records.
- [x] Recipient acceptance workflow preserving immutable creator attribution.
- [x] Expiration tracking (`expiresAt`) locking shared records on expiration without deleting personal data.

---

### Part 7: QA, Regression, Security & Release
- [x] Unit tests for `CoordinateHelperTest`, `SharingManagerTest`, and `SearchMatcherTest`.
- [x] Verified zero regressions in Camera Scan and Navigation.
- [x] Debug APK compiled successfully (`app-debug.apk` ~29 MB).
- [x] Pushed clean commit to `main` branch on GitHub.
