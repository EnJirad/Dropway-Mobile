# Dropway Rebuild Progress & Verification Dashboard

## Current Status: Part 0 (Master Docs) COMPLETED · Part 1 (New Architecture Foundation) COMPLETED · Part 2 (Authentication & User Identity) NEXT

| Part | Title | Status | Primary Output Files | Verified Commit | Notes |
|---|---|---|---|---|---|
| **Part 0** | Architecture Audit & Master Docs | `COMPLETED` | `docs/rebuild/*.md` (12 files) | Verified | Complete 12 master architectural documents generated. |
| **Part 1** | New Architecture Foundation | `COMPLETED` | `domain/model/*`, `domain/repository/*`, `data/local/LocalHouseDatabase.kt`, `data/remote/api/*`, `data/repository/*` | Verified | Domain layer, repository interfaces, SQLite v4 schema migration, API client, and repository implementations verified with debug APK assembly. |
| **Part 2** | Authentication & User Identity | `NOT_STARTED` | `domain/usecase/auth/*`, `ui/screens/AuthScreen.kt` | - | Cloud auth, session persistence, role verification. |
| **Part 3** | Folder System & Organization | `NOT_STARTED` | `domain/model/Folder.kt`, `ui/screens/FoldersScreen.kt` | - | Folders CRUD, Mae Sot mapping, folder selector UI. |
| **Part 4** | Home Management & Data Layer | `NOT_STARTED` | `domain/model/House.kt`, `ui/screens/HousesScreen.kt` | - | Houses CRUD, optimistic UI, field validation. |
| **Part 5** | Navigation & Batch Processing | `NOT_STARTED` | `domain/model/NavigationHouse.kt`, `ui/screens/NavigationScreen.kt` | - | Multi-stop sequencing, parcel tracking, delivery actions. |
| **Part 6** | QR Code Sharing & Synchronization | `NOT_STARTED` | `data/sharing/QRShareManager.kt` | - | Vector QR generator, camera/gallery import, cryptographic signing. |
| **Part 7** | AI Vision & Label Scanner | `NOT_STARTED` | `data/remote/GeminiVisionClient.kt`, `ui/screens/ScanScreen.kt` | - | Gemini 2.0 Flash parser, field extraction, address matching. |
| **Part 8** | Map Engine & Geolocation | `NOT_STARTED` | `util/MapProviderRegistry.kt`, `ui/components/MapPickerDialog.kt` | - | 16 map tile providers, Google Satellite default, Leaflet bridge. |
| **Part 9** | Subscription & Monetization | `NOT_STARTED` | `data/sharing/SubscriptionConfig.kt` | - | Central pricing, feature gating, anti-tamper check. |
| **Part 10** | Security, Encryption & Integrity | `NOT_STARTED` | `data/sharing/VaultCryptoManager.kt` | - | Android KeyStore AES-256-GCM, HMAC verification, secure logging. |
| **Part 11** | Performance & Reliability | `NOT_STARTED` | `util/SearchMatcher.kt`, `data/local/LocalHouseDatabase.kt` | - | SQLite indices, compiled regex, background dispatchers. |
| **Part 12** | Offline-First Engine & Conflict Resolution | `NOT_STARTED` | `data/sync/SyncManager.kt` | - | Outbox queue worker, exponential backoff, conflict rules. |
| **Part 13** | End-to-End Verification & Production Readiness | `NOT_STARTED` | Final Release APK & Validation Report | - | Regression test suite, Mae Sot migration verification. |

---

## Verification Log
- **2026-11-22T05:35:00Z:** Initial mobile APK assembleDebug verified (`BUILD SUCCESSFUL in 2m 44s`, APK size 29.5 MB).
- **2026-11-22T05:38:00Z:** Master documentation suite created in `docs/rebuild/` (12 documents).
- **2026-11-22T05:52:00Z:** Part 1 completed and verified with cached fast-path build (`BUILD SUCCESSFUL in 3m 58s`, APK size 29M).
