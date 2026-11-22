# Dropway Mobile — Agent Guidelines & Rules

## 1. Mandatory Git Push Rule (กฎเหล็ก)

> **ให้ทำการ push ทุกครั้ง หลัง ทำงานสำเร็จแล้ว**
> **Mandatory Rule:** Always perform `git commit` and `git push` to the remote repository (`origin <branch>`) every time work or a task has been completed and verified successfully. Never leave completed work unpushed.

### Commit & Push Checklist
1. Review status with `git status -s` and diffs with `git diff`.
2. Ensure no unintended temporary files or credentials are staged.
3. Commit with a concise conventional commit message (e.g. `feat(...)`, `fix(...)`, `docs(...)`).
4. Push to remote: `git push origin main` (or the active branch).
5. Verify remote sync with `git status`.

---

## 2. Workspace & Build Protocol

- **Workspace Scope:** Only create, edit, read, run, and build project files inside `/workspace/dropway`. Do not use scratch directories outside the workspace.
- **Android Gradle Builds:**
  - Never use plain `gradle build`.
  - Never start or run a persistent Gradle daemon.
  - Build only the debug APK (`assembleDebug`).
  - **First build / clean build:**
    ```bash
    timeout 15m gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 assembleDebug --console=plain --stacktrace
    ```
  - **Cached fast-path build:**
    ```bash
    timeout 5m gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --offline --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 assembleDebug --console=plain --stacktrace
    ```

---

## 3. Architecture & Coding Standards

### UI & Styling
- **Zero Emoji:** Never use unicode emoji in UI labels, cards, or dialogs. Always use vector icons (Compose Material icons or vector drawables).
- **Edge-to-Edge Layout:** Screen content must fit naturally below status bar indicators (signal, battery, clock) and above navigation controls. Avoid double-insets caused by nested `Scaffold` components by setting `contentWindowInsets = WindowInsets(0, 0, 0, 0)` on nested/inner containers.
- **Action Order:** Consistent item actions order: Main content -> Edit action -> Delete action (Edit must precede Delete).
- **Confirmation Dialogs:** Destructive actions (like deleting records) must always display an explicit confirmation dialog identifying the item name/address with Cancel and Confirm buttons.

### Local-First Data & Performance
- **Local Storage:** Store records locally using SQLite (`LocalHouseDatabase`). The application must function 100% offline without requiring connection to external databases (Neon, etc.).
- **High-Performance Lists:**
  - Pre-compile regex patterns statically (`SearchMatcher.kt`). Never instantiate new `Regex(...)` within list items or tight loops.
  - Offload heavy transformations (e.g., district grouping, parsing) to background threads (`Dispatchers.Default` / `Dispatchers.IO`).
  - Debounce text search inputs (250–300ms) to preserve UI thread responsiveness.

### Subscription & Pricing
- **No Hardcoded Pricing:** Never hardcode prices or billing periods inside UI composables.
- **Central Configuration:** Centralize all subscription terms and pricing in `SubscriptionConfig.kt`.
- **Tamper Protection:** Use cryptographic signatures (`SharingManager.kt`) to prevent tampering with shared records and preserve immutable creator attribution.
