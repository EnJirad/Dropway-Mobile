# Dropway Test Strategy & Quality Assurance Plan

## 1. Testing Philosophy & Pyramid

Dropway follows a multi-tier testing strategy to ensure field reliability, zero crashes, and data consistency:

```
           / \
          /   \
         / UI  \        Compose Screen Tests & Visual Verification
        /-------\
       / Integr- \      SQLite Database Migrations & Sync Outbox Queue
      /   ation   \
     /-------------\
    /  Unit Tests   \   Domain Entities, Normalizers, Cryptography, Regex Matchers
   /-----------------\
```

---

## 2. Test Suites & Coverage Focus

### Layer 1: Unit Tests
- **Domain & Models:** Validation of `House`, `Folder`, `NavigationHouse` constructors, defaults, and copy methods.
- **Coordinate & Search Normalizers (`CoordinateHelper.kt`, `SearchMatcher.kt`):**
  - Thai address normalization (e.g. converting `ต.แม่ปะ` -> `ตำบลแม่ปะ` or matching `123/4` against `123/4 ม.2`).
  - Strict pre-compiled regex performance under 1ms for 5,000 items.
- **Cryptographic Operations (`VaultCryptoManager.kt`):**
  - KeyStore key generation, AES-256-GCM encryption/decryption roundtrip.
  - HMAC signature generation and tampering rejection.
- **Label Parsing (`LabelParser.kt`, `LabelValidator.kt`):**
  - Phone extraction regex test suite (100+ variations of Thai phone numbers).
  - Address tokenization and field separation.

### Layer 2: Integration Tests
- **SQLite Migrations (`LocalHouseDatabase.kt`):**
  - Verify migration from Schema v1 to v2 preserving all 4,530 Mae Sot records.
  - Verify default folder creation and `folder_houses` link population.
- **Outbox Queue & Sync Processing:**
  - Verify mutations are queued atomically during offline writes.
  - Verify idempotent replay of mutations does not produce duplicates.

### Layer 3: Build & Static Verification
- **Compilation & Linting:** Strict adherence to zero-warning compilation.
- **Build Verification Protocol:**
  ```bash
  timeout 5m gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --offline \
    --init-script /root/.gradle/init.d/pocketdev-android.gradle \
    -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 \
    assembleDebug --console=plain --stacktrace
  ```
- **APK Verification:** Check output file size, dex count, and manifest declarations.

---

## 3. Regression & Acceptance Checklist
Before marking any Part complete:
- [ ] Debug APK builds cleanly with 0 errors.
- [ ] No emojis in UI code or resource strings.
- [ ] Action order preserved: Main content -> Edit action -> Delete action.
- [ ] All database queries executed off UI thread.
- [ ] Git commit and push performed per `AGENT.md`.
