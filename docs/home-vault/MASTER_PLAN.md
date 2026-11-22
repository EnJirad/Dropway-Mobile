# DROPWAY MOBILE — HOME VAULT REBUILD & OPTIMIZATION MASTER PLAN

## 1. Executive Summary & Architecture Overview

Dropway Mobile is a high-performance Android courier delivery application built with Kotlin and Jetpack Compose.
The **Home Vault (คลังบ้าน)** serves as the driver's local repository of delivery addresses, coordinates, customer phone numbers, and location delivery notes.

Currently, the application contains real-world data consisting of **4,530 delivery locations** in Mae Sot (ตาก, ประเทศไทย) stored in `.ai/houses_rows.csv`.
This Master Plan defines the progressive architectural transformation of the Home Vault into a world-class, offline-first, high-performance, and secure mobile system.

---

## 2. Existing Implementation Assessment & Problems Found

### 2.1 Layout Hierarchy & System Insets (Double Padding Bug)
- **Source File:** `app/src/main/java/com/pocketdev/dropway/MainActivity.kt` (lines 58–127) and `ui/screens/HousesScreen.kt` (lines 121–173).
- **Problem:**
  - `MainActivity` contains a root `Scaffold` providing `innerPadding` (which includes top status bar insets and bottom navigation bar insets).
  - `HousesScreen` is placed inside `Column(modifier = Modifier.padding(innerPadding))` and has its own `Scaffold` with `TopAppBar`.
  - `TopAppBar` defaults to adding `TopAppBarDefaults.windowInsets` (which includes `WindowInsets.statusBars`).
  - As a result, the status bar inset is applied **twice**, creating excessive wasted blank space at the top of the screen.
  - Additionally, `HousesScreen.kt` line 295 adds `bottom = 80.dp` to `LazyColumn.contentPadding` while `innerPadding` already accounted for the bottom navigation bar, leaving an oversized blank area at the bottom.
- **Impact:** Screen content is vertically compressed, off-balance, and does not fit naturally across varying display aspect ratios, punch-hole cameras, or gesture navigation bars.

### 2.2 List Rendering & Scroll Performance Bottlenecks
- **Source File:** `app/src/main/java/com/pocketdev/dropway/ui/screens/HousesScreen.kt` (lines 85–119, 421–428, 552).
- **Problem:**
  - Line 85–88: `districts` computation runs `houses.mapNotNull { SearchMatcher.extractDistrict(it.address) }.distinct()` on the **Main (UI) thread** inside `remember(houses)`. For 4,530 houses, this executes complex regex pattern matching 4,530 times during composition, causing noticeable UI freeze/jank upon loading.
  - Line 91–119: Search query filtering has **no debouncing**. Every single character typed immediately triggers coroutine cancellation and re-execution of full dataset ranking on 4,530 records.
  - Line 421–428: In `HouseCard`, dynamic `Regex("""ม\.|หมู่|moo|\bm\.""", RegexOption.IGNORE_CASE)` is instantiated dynamically inside Compose lambdas for each newly visible card instead of being a pre-compiled static pattern.
  - Line 552: Uses emoji `"📝"` in the UI, violating Global Rule 8 (professional icon system only).
- **Impact:** Frame drops during fast scrolling and search input lag.

### 2.3 Remote Dependency & Lack of Local-First Architecture
- **Source File:** `app/src/main/java/com/pocketdev/dropway/data/remote/NeonClient.kt` (lines 206–234) and `HousesScreen.kt` (lines 62–78).
- **Problem:**
  - The Home Vault directly queries a remote PostgreSQL Neon database over HTTP REST (`SELECT * FROM public.houses ORDER BY created_at DESC LIMIT 50000;`).
  - If network is weak or the database URL is unconfigured, the driver cannot view or search any homes.
  - Delivery drivers operate in remote rural areas (e.g. Mae Sot border areas) with intermittent cellular connectivity where personal address lookups must work 100% offline.
- **Impact:** Zero offline functionality, slow startup, and high remote bandwidth usage.

### 2.4 Edit Form & Map Picker Deficiencies
- **Source File:** `app/src/main/java/com/pocketdev/dropway/ui/components/CommonDialogs.kt` (lines 17–120).
- **Problem:**
  - Coordinates are split into separate Latitude and Longitude fields rather than a single unified `"lat,lng"` field.
  - No interactive map picker with center pin or satellite toggle.
  - Contains an unnecessary `"จำนวนพัสดุปกติ"` (parcel quantity) field on the house record, which clutters the interface.
  - Delete action is triggered without a clear explanation of consequences.

---

## 3. Proposed Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       DROPWAY MOBILE                        │
│                 Home Vault (คลังบ้าน) Screen                 │
└──────────────────────────────┬──────────────────────────────┘
                               │
       ┌───────────────────────┴───────────────────────┐
       ▼                                               ▼
┌──────────────────────────────┐        ┌──────────────────────────────┐
│  Presentation Layer          │        │  Interactive Map & Location  │
│  - Edge-to-edge Insets       │        │  - Center Pin Map Picker     │
│  - Virtualized LazyColumn    │        │  - Standard / Satellite      │
│  - Debounced Search & Rank   │        │  - GPS Current Location      │
│  - Compact Far-Right Actions │        └──────────────────────────────┘
│  - Safe Delete Dialog        │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│  Domain & Utility Layer      │
│  - Precompiled Regex Engine  │
│  - Coordinate Normalizer     │
│  - Creator & Sharing Model   │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│  Local-First Storage         │
│  - SQLite / Room Database    │
│  - Pre-seeded 4,530 records  │
│  - Offline Instant Search    │
│  - File Import/Export Backup │
└──────────────┬───────────────┘
               │ (Optional / Paid Subscription)
┌──────────────▼───────────────┐
│  Secure Sharing Gateway      │
│  - Server-Side Token Grants  │
│  - E2E Encrypted Payloads    │
│  - Subscription Gate (THB 25)│
└──────────────────────────────┘
```

---

## 4. Part-by-Part Roadmap

### PART 1: Responsive Layout & Scroll Performance
- **Scope:**
  - Fix status bar and navigation bar insets across `MainActivity` and `HousesScreen`.
  - Eliminate double status bar padding and excess bottom padding.
  - Pre-compile all regular expressions statically in `SearchMatcher`.
  - Move district extraction and heavy list processing to background worker threads.
  - Implement 250ms debounced search to guarantee 60 FPS typing and scrolling.
  - Verify performance with the full 4,530-record dataset (`.ai/houses_rows.csv`).
- **Dependencies:** None.

### PART 2: Home Vault UI & Interactions
- **Scope:**
  - Move Add Home action to a compact toolbar button aligned to the far right.
  - Use clean Lucide/Material icon with label "เพิ่มบ้าน".
  - Standardize row action order: Content -> Edit -> Delete (Edit precedes Delete).
  - Guard Delete action with a descriptive confirmation dialog stating the specific house name/address.
  - Replace any emoji in the UI with standard vector icons.
- **Dependencies:** Part 1.

### PART 3: Home Edit Form & Map Picker
- **Scope:**
  - Unified Full Name field and Phone field with proper validation.
  - Single Coordinate field accepting `"lat,lng"` with real-time range validation.
  - Map Pin button opening an interactive map picker.
  - Interactive map with fixed center pin, satellite/standard toggle, and GPS current location button.
  - Free-form multi-line Notes/Observation field.
  - Remove redundant parcel quantity field from edit form.
- **Dependencies:** Part 2.

### PART 4: Local-First Home Vault Storage
- **Scope:**
  - Implement a fast structured local SQLite storage engine with indexed queries on phone, address, and name.
  - Load and persist 4,530 houses locally; guarantee 100% offline functionality.
  - Implement Backup & Export / Import feature with validation and integrity checks.
- **Dependencies:** Part 3.

### PART 5: Home Ownership, Creator Identity & Shared Data Model
- **Scope:**
  - Define immutable creator schema (`creator_id`, `creator_name`, `created_at`, `record_version`, `source_type`).
  - Separate personal vs shared records cleanly in the data model.
- **Dependencies:** Part 4.

### PART 6: Subscription-Based Secure Sharing
- **Scope:**
  - Define secure server-side sharing architecture with configurable pricing (THB 20–30/month).
  - Server-side subscription check, share token exchange, and automatic access expiration/revocation.
  - Document privacy and encryption boundaries.
- **Dependencies:** Part 5.

### PART 7: QA, Regression, Security & Release
- **Scope:**
  - Cross-device validation (varying screen sizes, densities, navigation modes).
  - Benchmark performance across 4,530 real records.
  - Full regression test on ScanScreen and NavigationScreen.
  - Build debug APK and push to Git.
- **Dependencies:** Parts 1–6.

---

## 5. Performance Targets & Acceptance Criteria

| Metric | Target | Current Baseline |
|---|---|---|
| Screen Top Spacing | Natural edge-to-edge with system status bar (no double padding) | Double status bar padding (~48–64dp wasted) |
| Scroll Frame Rate | Stable 60 FPS with 4,530 records | Jank on initial load and fast scroll |
| District Filter Calc | < 15ms (background dispatcher) | ~80–120ms blocking UI thread during composition |
| Search Response Time | < 50ms with 250ms debounce | Immediate burst recomputation per keystroke |
| Cold Start Offline Read | < 100ms from local storage | Blocked completely without remote Neon DB |

---

## 6. Testing Strategy
1. **Layout & Inset Inspection:** Verify layout across gesture navigation and 3-button navigation bars.
2. **Dataset Stress Test:** Load the entire 4,530 rows from `.ai/houses_rows.csv` into the list.
3. **Unit Tests:**
   - SearchMatcher regex optimization tests.
   - Coordinate parsing & validation tests.
   - Debounce & filter consistency tests.
4. **Build Verification:** Assemble debug APK using the designated no-daemon cached command.
