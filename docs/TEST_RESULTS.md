# Executed validation — 3 November 2026

Final command (Java 21.0.11, Gradle 9.6.0):

```text
gradlew.bat assembleDebug testDebugUnitTest lintDebug bundleRelease connectedDebugAndroidTest --no-daemon --max-workers=2 --continue
BUILD SUCCESSFUL in 1m 46s
```

| Check | Result |
|---|---|
| Debug APK | PASS — `app/build/outputs/apk/debug/app-debug.apk` |
| JVM tests | PASS — 6 tests, 0 failures |
| Android lint | PASS — 0 errors, 15 warnings |
| Release bundle | PASS — `app/build/outputs/bundle/release/app-release.aab`; unsigned |
| Device tests | PASS — 5 tests, 0 failures, API 37 / Android 17 emulator |

Unit tests exercise crop aspect ratio and bounds across zoom/pan values, mm-to-pixel and mm-to-PDF-point conversions, sheet margins and oversized photos, ISO country lookup, verification status and original-export restrictions. The 4 × 6 inch sheet fits four 35 × 45 mm photos with the configured 6 mm margin / 3 mm gap; six would exceed the printable height.

Device tests exercise searchable country selection and document selection, requirements navigation, Settings/Privacy navigation, local image import through the ViewModel followed by library → editor → quality → export navigation, Room persistence/deletion, real JPEG encoding at 600 × 600 pixels, and real PDF file generation.

Lint warnings cover newer SDK/dependency suggestions (SDK 36 is explicitly required and newer Compose/Nav releases need SDK 37), legacy backup metadata, resource folder naming, kapt-to-KSP migration, and optional KTX conveniences. No lint errors are suppressed and no baseline is used. Deprecated Locale/icon compiler warnings remain.

The merged release/debug manifests were inspected: no internet, camera, location or broad photo/storage permissions. AndroidX contributes its signature-only dynamic-receiver permission. Original photos are private; only explicit cache capture/export paths are provider-exposed. Release bundle signature verification reports **unsigned**, as expected without publisher signing configuration.

Home was visually inspected in light/dark portrait and dark landscape after rotation. A dark-theme primary-button contrast issue found during this review was corrected to explicit white text, then all five Gradle tasks above were rerun successfully.

Earlier iterations had a PDF close-handling compile error, a wrong test expectation for sheet capacity, Windows SDK path escaping, and an old Espresso InputManager incompatibility. These were corrected. A final country test matcher was narrowed to exclude the editable search field. The latest reports above replace those failed attempts.

## Reports

- Full final log: `docs/validation.log`
- Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Device report: `app/build/reports/androidTests/connected/debug/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`
- Native app screenshots: `docs/screenshots/`

## Not executed / still required before publication

- Android 8, 10, 12, 14, 15 and 16 devices/emulators are not installed here. API 37 testing does not substitute for that matrix.
- Physical camera hardware, real printer measurements, low-storage fault injection, exhaustive invalid/large-image cases, process-death recovery during external camera/document activities, and a full TalkBack/accessibility audit.
- Signed production bundle, Play Console/pre-launch checks, publisher identity/privacy contact and hosted privacy policy.
- No automated face/head/background acceptance verification is provided; those checks remain manual.
