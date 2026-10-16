# Repository survey (17 Oct 2026)

What was in `MrPresident03/Wine-Cellar` before House of Vino work started, and what happens to it.

## What existed

**Wine Cellar v2.1** — an Android-only app (applicationId `com.aistudio.winecellar.vntgrk`, versionCode 7), about 5,900 lines of Kotlin.

| Area | Implementation | Notes |
|---|---|---|
| UI | Jetpack Compose, Material 3, one `WineCellarViewModel` | Screens: Inventory (18 × 10 rack grid), Climate, Settings, Login |
| Data | Firebase Firestore as the single source of truth, with its offline cache | One document per bottle; position = `gridRow/gridCol`; price as `Double` |
| Accounts | Firebase Auth (Google, email/password) | Shared cellar via 6-character invite codes, enforced by `firestore.rules` |
| Photos | Base64 JPEG thumbnails in the bottle document; full photo in a `photos` sub-collection | |
| Scanning | ML Kit on-device OCR + `LabelParser` heuristics | Fills winery, vintage, varietal, classification |
| Climate | ESP32 sketch (`firmware/`) posts temperature/humidity to Firestore; WorkManager alert worker | Not in the House of Vino spec |
| Import | `LegacyImporter` reads v1's Room database and older cloud copies | |
| Build | GitHub Actions on every push to `main`: builds a debug APK and publishes it as a GitHub Release; a pushed `.zip` is unpacked over the repo first | |
| Tests | Template examples only | |

## Assessment against the House of Vino spec

- **Stack differs.** The spec requires Kotlin Multiplatform, Compose Multiplatform, SQLDelight and Supabase. The existing app is Android-only with Firestore. Porting in place is not practical; the new app is built alongside.
- **Data model is too thin.** One flat bottle record mixes wine identity, inventory and position, has no vintages-as-entities, no acquisition batches, no ledger, and stores money as `Double`.
- **Useful, carried over:**
  - `LabelParser` heuristics (vintage vs founding year, varietal aliases, classification phrases, winery prominence scoring) → ported to common code in `shared/core/scan`.
  - Grape list and style guessing → `shared/core/model/WineStyle.kt`.
  - Invite-code sharing → becomes household membership with server-side invites.
  - The rack grid concept → gridded locations with optional bottle positions.
  - v1/v2 importers → a Firestore export path in Import (later milestone; nothing is migrated without the owner's go-ahead).
  - Climate sensor → kept in `firmware/` and listed as a post-1.0 module; its cloud endpoint will need replacing when Firestore is retired.

## Where it went

- `legacy/android-v2/` — the complete v2.1 app, its Firestore rules, README, build workflow (as JSON, inert) and the debug keystore, unchanged. Not part of the new Gradle build.
- `firmware/` — unchanged.
- `main` is untouched. All House of Vino work is on the `house-of-vino` branch, because pushes to `main` publish an APK that existing phones install.

## Issues found in the live v2.1 app (not fixed — they live on `main`)

1. `debug.keystore.base64` is committed to a **public** repository and is the key that signs released APKs. Anyone can build an APK that Android accepts as an update to the installed app. Recommended: stop distributing debug-signed builds; move to a private release key.
2. `firestore.rules` contains a "TEMPORARY" rule letting any signed-in Firebase user read every `cellars/cellar_*/bottles` collection.
3. Any cellar member can rewrite the `members` list (remove others). Owner-only membership changes would be safer.
4. `firmware/esp32_cellar_sensor.ino` embeds the Firebase Web API key. That key is not secret by design, but it should be restricted to the Firestore API in Google Cloud.
