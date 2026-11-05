# Release builds

## Windows installer

```bash
cd desktop
npm install
npm run dist
```

Output: `desktop/release/Phone Viewer Setup <version>.exe` (NSIS, per-user, one click). `desktop/release/` is gitignored.
The first run downloads NSIS tooling from GitHub into the electron-builder cache.

The installer is **not code-signed**, so Windows SmartScreen warns on first run ("More info" → "Run anyway"). There is no auto-update.

## Signed Android APK

1. Create a keystore once, **outside the repo**, and back it up. Losing it means you can never ship an update to the same app.

   ```bash
   keytool -genkeypair -v -keystore %USERPROFILE%\phone-viewer-release.jks -alias phoneviewer -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `android/keystore.properties` (gitignored, never commit it):

   ```properties
   storeFile=C:/Users/<you>/phone-viewer-release.jks
   storePassword=...
   keyAlias=phoneviewer
   keyPassword=...
   ```

   Or set env vars instead: `PV_KEYSTORE`, `PV_KEYSTORE_PASSWORD`, `PV_KEY_ALIAS`, `PV_KEY_PASSWORD`. A relative `storeFile` is resolved from `android/`.

3. Build:

   ```bash
   cd android
   ./gradlew assembleRelease
   ```

Output: `android/app/build/outputs/apk/release/app-release.apk`.
Without a keystore configured, the same command still succeeds and writes `app-release-unsigned.apk`, which Android will not install.

Check the signature: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` (in `<Android SDK>/build-tools/<version>/`).
