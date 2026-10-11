# Wine Cellar

Android app for tracking a wine rack (18 × 10 slots), shared between phones, with live
temperature/humidity from an ESP32 sensor.

## Version 2.0 changes
- **Sync rebuilt.** Firestore is now the single source of truth (with its built-in offline cache).
  Every bottle has its own permanent ID, so edits, moves, swaps and deletes sync correctly and
  can't be undone or duplicated by another phone.
- **Real accounts.** Google sign-in (or email + password) through Firebase Authentication. A cellar is shared with people you invite
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
1. Firebase console → ⚙ **Project settings** → **Your apps** → the Android app → **Add fingerprint**, and add both
   keys of the signing certificate used by these builds:
   - SHA-1: `47:80:13:23:3D:F4:15:26:8A:8E:FB:48:56:4B:80:9E:7F:AC:1D:4F`
   - SHA-256: `DB:8C:2C:30:0C:F3:C8:45:FA:57:43:3A:83:D9:7B:B5:75:DD:C0:76:7F:CF:C8:B0:6A:99:64:D7:28:54:7D:5F`
2. On the same page, download **google-services.json** and upload it into the `app/` folder of this repo
   (it now contains the Google sign-in client ID). The build workflow always keeps the repo's copy of this file.
3. **Authentication** → **Sign-in method**: Google must be enabled. Email/Password is optional (enable it if you
   also want the email option on the login screen).
4. **Firestore Database** → **Rules** → replace everything with the contents of
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
