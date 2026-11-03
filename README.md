# Passport Photo Maker

Native Kotlin / Jetpack Compose Android application. Application ID `com.passportphoto.worldwide`; Android 8+ (API 26), compile/target API 36, Java bytecode 17. No internet, camera or broad storage permissions. Camera capture delegates to the installed camera app; gallery uses Android Photo Picker / its AndroidX fallback. No WebView.

## Build

Open this directory in Android Studio. Install SDK 36 and a compatible JDK (17 or 21). For reproducible builds here, use `C:\Users\User\.jdks\jbr-21.0.11` here.

```powershell
$env:JAVA_HOME='C:\Users\User\.jdks\jbr-21.0.11'
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat bundleRelease
.\gradlew.bat connectedDebugAndroidTest
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/bundle/release/app-release.aab`. Without signing environment variables the release bundle is unsigned and cannot be uploaded as a signed production release.

Release signing reads `PASSPORT_KEYSTORE`, `PASSPORT_STORE_PASSWORD`, `PASSPORT_KEY_ALIAS`, `PASSPORT_KEY_PASSWORD`. Keep keys and passwords outside source control. No signing key is generated or committed.

## Workflow

Select a country and document on Home, then take or import a photo. A private original and a Room project are saved immediately. Pinch, drag, use zoom, undo or reset; set custom millimetres where needed. Confirm to view advisory quality results. Export a cropped JPEG, an untouched original, or an accurately sized multi-photo PDF. Use My Photos to reopen, edit, export or delete projects. Settings persists country, document, theme, JPEG quality and default export route with DataStore.

The hero is built from Compose controls and original geometric artwork, not the supplied screenshot. It uses a stylised portrait symbol rather than the reference photograph. Small screens scroll; system insets and dark theme are supported.

## Architecture

The build uses AGP 9.4.1 / Gradle 9.6.0 with built-in Kotlin and the compatible Compose compiler. AndroidX versions are pinned to stable releases compatible with compile SDK 36; Compose BOM 2026.01.00 is used because the September 2026 BOM requires SDK 37. Espresso 3.7.0 includes the InputManager compatibility fix needed by the installed API 37 emulator. Room uses the AGP legacy kapt bridge.

- `ui/PassportApp.kt`: Compose screens, Navigation Compose, system activity-result launchers, export destination selection.
- `ui/PhotoViewModel.kt`: lifecycle state, SavedStateHandle, import, project updates, deletion and asynchronous jobs.
- `data/Storage.kt`: Room metadata and DataStore preferences.
- `requirements/Requirements.kt`: searchable ISO country directory and route-specific sourced requirements.
- `imaging/Geometry.kt`: pure crop and physical PDF layout mathematics.
- `imaging/PhotoEngine.kt`: orientation-aware bounded decoding, JPEG, PDF, print adapter and advisory quality statistics.

## Image geometry and privacy

Crop coordinates are computed in source-image space. The same crop function drives the editor and output. Zoom never drops below cover-fit; pan clamps to image bounds. JPEG output uses 300 pixels/inch at the selected physical dimensions. PDF positions are converted from millimetres with `72 / 25.4` points per millimetre. A 50.8 mm photo occupies exactly 144 PDF points. The sheet uses 6 mm margins and 3 mm gaps. Printing still requires Actual size / 100%; printer behaviour must be checked physically.

Processing decodes images to at most about 2400 pixels on the longer edge (power-of-two sampling on API 26/27). This bounds memory and may downsample very high-resolution input before cropping; strong zoom may therefore lose detail. Originals remain byte-identical in private storage. Imports have a 100 MB compressed-file cap. No facial editing or background replacement is performed. Original exports preserve metadata, including any camera location tags; cropped JPEG exports do not copy EXIF metadata.

Only cache capture/export subdirectories are exposed through FileProvider with temporary URI grants. Originals are never provider-exposed. App backup is disabled. Cache files older than 24 hours are cleaned at startup. Removing a project deletes its private original; files explicitly exported outside the app are independent.

## Requirements coverage and limitations

Printed measurements sourced for US, Canada, Australia and France; UK digital guidance is sourced separately and defaults to original export. Canadian and French requirements include professional/approved capture restrictions: an app-generated sheet does not replace those services. Germany's certified transmission route is unavailable. India, Pakistan, Bangladesh and Nigeria dimensions remain unverified; official source links and notes are supplied. Other countries and all non-passport document types offer explicitly unverified custom sizes. No worldwide compliance claim is made.

No offline face detector is bundled. Face positioning, head size and background checks are manual; the app does not pretend to verify them. Blur and luminance heuristics are advisory and may produce false positives. There is no guarantee of acceptance. No automatic file-size optimisation for application portals. Camera preview and retake are provided by the system camera app where available.

The publisher must complete the privacy contact, host the policy, configure release signing, and perform the device/physical-print checks in `docs/PLAY_RELEASE_CHECKLIST.md`. See `docs/TEST_RESULTS.md` for checks actually executed; an authored test is not evidence that it ran.

