# Google Play release checklist

Reviewed 3 November 2026 against official sources. Recheck before submission; policies and console declarations can change.

- Target/compile SDK 36; minimum SDK 26. Confirm the current target requirement in [Play Console guidance](https://support.google.com/googleplay/android-developer/answer/11926878).
- Configure publisher-owned release/upload key using the environment variables in README; enrol in Play App Signing. Never upload the unsigned bundle as a production release.
- Complete publisher identity and privacy contact in the policy; host it at a public active HTML URL. Link it from the store listing. See [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Complete Data Safety based on actual shipped behaviour: no developer image collection, transfer, analytics, ads or accounts. User-directed sharing/export is disclosed. Reassess if SDKs or network access change. See [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469).
- Complete content-rating questionnaire accurately: utility / photography app; no social feed, in-app user content publishing, ads, gambling or purchases in this version. Answer the console questions; do not assume a final rating.
- Declare no government affiliation, no guaranteed acceptance, and the limits of custom/unverified presets.
- Check the merged manifest contains no broad image/video storage permissions. Gallery uses the picker and camera uses an external capture activity.
- Review 16 KB page size compliance of included AndroidX native binaries using bundle analysis and Play pre-launch checks.
- Test Android 8, 10, 12, 14, 15 and 16; installed API 37 emulator is supplemental, not a substitute.
- Test small/large screens, accessibility font scaling/TalkBack, light/dark, rotation, process death, camera/gallery cancellation, invalid/large images, low storage, deletion and revoked document access.
- Print A4, 4 × 6 and Letter at actual size. Measure photos using a ruler. Inspect printer margin/scale behaviour.
- Run unit tests, lint, instrumentation, signed release bundle and Play pre-launch report.
- Supply 512 × 512 PNG store icon based on the adaptive vector, 1024 × 500 feature graphic, and actual app screenshots. Capture Home, Editor, My Photos, Export and Requirements. Do not imply sample images were approved by an authority.
- Complete app access (no login), ads (none), target audience, content rating, pricing/countries, support details and required testing track based on developer account type.

## Store listing draft

**Name:** Passport Photo Maker

**Short description:** Prepare passport and ID photos privately, with custom sizes and printable sheets.

**Full description:**

Prepare passport, visa and ID photos on your Android device. Import a photo or use your camera, position it with crop guides, review advisory quality guidance and export a JPEG or printable PDF sheet. Choose A4, 4 × 6 inch or US Letter paper. Keep and reopen photo projects locally.

Photos are processed offline. No mandatory account, advertising or analytics. Export an untouched original when your application requires it. Review sourced requirements and use custom dimensions when your route is not verified.

Requirements vary by country and application method. Some authorities require approved photographers, booths or certified digital submission that this app cannot provide. Quality advice is advisory and does not guarantee acceptance. Passport Photo Maker is independent and is not affiliated with any government.
