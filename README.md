# Wine Cellar

Android app for tracking a wine rack (18 × 10 slots), shared between phones, with live
temperature/humidity from an ESP32 sensor.

## Version 2.0 changes
- **Sync rebuilt.** Firestore is now the single source of truth (with its built-in offline cache).
  Every bottle has its own permanent ID, so edits, moves, swaps and deletes sync correctly and
  can't be undone or duplicated by another phone.
- **Real accounts.** Firebase email + password sign-in. A cellar is shared with people you invite
  using a 6-character code (Settings → Shared cellar). Firestore rules lock each cellar to its members.
- **Import from version 1.** On first sign-in, "Create cellar & import my bottles" copies the old
  on-phone database (and the old cloud copy, if any) into the new cellar, including photos.
  The old data is never deleted; a backup copy is kept in the app's private folder.
- **Photos.** Full-resolution camera or gallery photos, rotated correctly, compressed and stored in
  Firestore so they appear on every phone (no paid Firebase plan needed).
- **Real sensor + alerts.** The ESP32 sketch in `firmware/` writes readings to the cloud. The Climate
  tab shows live data and history; optional notifications when the cellar goes above your limit.
- **Fixes.** Two bottles can no longer share a slot, deleting asks for confirmation, settings are saved,
  demo bottles and simulated readings are gone, and the 2,600-line inventory screen is split up.

## One-time Firebase setup (do this before installing 2.0)
1. Firebase console → **Authentication** → Get started → **Sign-in method** → **Email/Password** → Enable → Save.
2. Firebase console → **Firestore Database** → **Rules** → replace everything with the contents of
   [`firestore.rules`](firestore.rules) → **Publish**.

## Building the APK
Pushing to `main` runs `.github/workflows/main.yml`, which builds the debug APK and publishes it on the
**Releases** page. Builds are signed with the key in `debug.keystore.base64`, the same key as earlier
builds, so a new APK installs as an update and keeps the phone's data. **Don't uninstall the old app first.**

If a `.zip` of the project is uploaded to the repo root, the workflow unpacks it over the repo,
commits the result and then builds.

## Sensor
See `firmware/esp32_cellar_sensor/esp32_cellar_sensor.ino`. The cellar ID and sensor key it needs are
shown in the app under Settings → Temperature sensor.

## Data layout (Firestore)
```
users/{uid}                       email, cellarId
cellars/{cellarId}                name, ownerUid, members[], memberEmails[], sensorKey, alertThreshold
cellars/{cellarId}/bottles/{id}   one document per bottle
cellars/{cellarId}/photos/{id}    full-size photo for bottle {id}
cellars/{cellarId}/climate/{id}   temperature, humidity, ts (from the ESP32)
invites/{code}                    cellarId, createdBy, expiresAt
```
