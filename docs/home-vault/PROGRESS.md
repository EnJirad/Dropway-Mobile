# DROPWAY MOBILE — HOME VAULT PROGRESS TRACKER

This document tracks execution progress, verified criteria, and open tasks for the Home Vault rebuild.

---

## Overall Status Dashboard

- **Phase 0 — Investigation & Master Plan:** ✅ COMPLETED
- **Part 1 — Responsive Layout & Scroll Performance:** ✅ COMPLETED
- **Part 2 — Home Vault UI & Interactions:** 🔄 READY TO START
- **Part 3 — Home Edit Form & Map Picker:** ⏳ PENDING
- **Part 4 — Local-First Home Vault Storage:** ⏳ PENDING
- **Part 5 — Ownership, Creator Identity & Shared Data Model:** ⏳ PENDING
- **Part 6 — Subscription-Based Secure Sharing:** ⏳ PENDING
- **Part 7 — QA, Regression, Security & Release:** ⏳ PENDING

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
- [ ] **2.1 Add Home Button:**
  - [ ] Move Add Home action to a compact toolbar button aligned to the far right.
  - [ ] Lucide/Material plus icon with label "เพิ่มบ้าน".
  - [ ] Remove oversized floating action button if redundant.
- [ ] **2.2 Row Action Order:**
  - [ ] Standardize action layout: Card Content -> Edit Button -> Delete Button (Edit precedes Delete).
  - [ ] Icon-only controls with accessible descriptions.
- [ ] **2.3 Delete Confirmation Dialog:**
  - [ ] Dialog explicitly identifies house full name or address to be deleted.
  - [ ] Cancel and Confirm Delete buttons; zero one-tap accidental deletions.

---

### Part 3: Home Edit Form & Map Picker
- [ ] **3.1 Required Fields:**
  - [ ] Unified Full Name field.
  - [ ] Phone field with normalization.
  - [ ] Single Coordinate field (`"lat,lng"`) with range validation.
  - [ ] Map Pin button on the right of coordinate field.
  - [ ] Free-form multi-line notes/observations field.
  - [ ] Remove parcel quantity field from edit form.
- [ ] **3.2 Interactive Map Picker:**
  - [ ] Center-pinned interactive map (user moves map under pin).
  - [ ] Standard and Satellite map layers toggle.
  - [ ] Current GPS location button with runtime permission handling.
  - [ ] Apply selected coordinates back to the form.

---

### Part 4: Local-First Home Vault Storage
- [ ] **4.1 Local Database Engine:**
  - [ ] Structured local storage (SQLite) with schema versioning.
  - [ ] Fast indexed lookups by phone, address, and name.
  - [ ] Pre-seed 4,530 houses from `.ai/houses_rows.csv` into local storage.
- [ ] **4.2 Offline Operation:**
  - [ ] Full functionality without network connection or Neon credentials.
- [ ] **4.3 Backup & Export/Import:**
  - [ ] JSON/CSV backup file export via Android Intent share.
  - [ ] Backup file import with schema validation and duplicate detection.

---

### Part 5: Home Ownership & Creator Identity
- [ ] Immutable creator ID, creation timestamp, and record version in schema.
- [ ] Clear distinction between personal user records and shared records.

---

### Part 6: Subscription-Based Secure Sharing
- [ ] Configurable pricing model (THB 20–30/mo).
- [ ] Server-side subscription validation and revocable token-based share grants.
- [ ] Lock access on expired subscriptions without destroying local data.

---

### Part 7: QA, Regression & Release
- [ ] Comprehensive test execution and reporting.
- [ ] Verify zero regressions in Scan Screen and Navigation Screen.
- [ ] Build final debug APK and commit/push to Git.
