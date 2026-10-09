# PayDar validation

Date: 6 October 2026. This records observed checks, not a production certification.

## Website polish — 8 October 2026

- Published source: `bc587cd6e86c3e774beb9dd7595f7c7f0194e8a7`.
- Graphite materials, studio reflections, app/device angle controls, damped pointer movement and scroll rotation.
- Sticky navigation and reading progress, tighter hero spacing, horizontal mobile toolkit and revised walkthrough sizing.
- Shared model load/parse; second scene loads near the walkthrough; background/offscreen animation pauses; WebGL fallback stage controls are independent of rendering.
- `node --check site/dist/main.js` passed. Four Node tests passed: WebGL-independent stage controls, reduced-motion selection, sticky-offset stage calculation, and honest UPI validation results.
- All local HTML/CSS file references resolved. APK SHA-256 is unchanged.
- Browser access was rejected by the saved permission setting even after the user enabled it. The new rendered layout, reflections and motion have NOT been visually verified. Screenshots below belong to the previous version.

## Website — original version, 6 October 2026

- JavaScript syntax check passed with `node --check site/dist/main.js`.
- 32 local HTML asset/link references resolved; no missing files.
- Desktop browser: licensed GLB loaded with the PayDar screen correctly mapped to its rounded display. Hero pointer/scroll motion and the sticky walkthrough rendered without console warnings or errors.
- Walkthrough controls selected scanning, review and result stages; the screen and caption changed with the selection.
- Toolkit tabs changed content, including ArrowDown keyboard navigation from MuleGuard to WrongPay Rescue.
- Malformed UPI input returned a format error. A conventionally formatted ID returned an explicit unverified result. No false safe verdict.
- FAQ expanded and showed the limitations of a missing local record.
- Mobile browser at 390 × 844: no horizontal document overflow; navigation opened and selected a section; the 3D walkthrough and stage controls remained usable.
- Desktop and mobile screenshots are in `docs/screenshots/`.
- Reduced-motion handling and offscreen/background rendering pauses were reviewed in source. Reduced-motion OS emulation was not available in the connected browser.

## Android beta 0.2.0 — 10 October 2026

- `gradlew.bat testDebugUnitTest assembleDebug` succeeded with 45 tests: 19 engine, 7 original SQLite, 12 transaction parser/CSV and 7 transaction storage/migration tests; zero failures or errors.
- Covers debits/credits/refunds, OTP/request/balance rejection, pending/failed statuses, counterparty extraction, reference matching across sources, opposite directions, quoted CSV round trips, invalid rows, formula-prefix neutralization, restart persistence, edits, deletion, schema upgrades and capture opt-in defaults.
- Merged manifest confirms receipt sharing and the notification-listener service are registered; INTERNET remains absent.
- APK: versionCode 2 / `0.2.0-beta`, Android 8.0+, 16,477,100 bytes. SHA-256: `b51e5b20d2c8eef84ebe4e075081e0f0cace590b5a452165742d429d9d703b91`.
- Signing certificate SHA-256: `b756eabae81ef8ac221950c783a3ba8be0b2dc524e452b455a5f9c43a37857f5`; matches the previous beta.
- `adb devices` reported no attached Android hardware. Compose layout, system permissions, live notifications from payment apps, SMS/device formats and share intents are NOT physically verified. Automated source tests do not establish full cross-app coverage.
- Website download/release/privacy notes updated; local links resolved. Browser QA was not retried because its saved permission denial remains unresolved.

## Android — original beta

`gradlew.bat testDebugUnitTest assembleDebug` completed successfully. All 26 tests passed: 19 engine tests and 7 SQLite persistence tests, with zero failures or errors. Persistence coverage includes restart survival, paused subscriptions, import deduplication, atomic rejected lists and complete local deletion. A PRAGMA initialization failure found by these tests was fixed before packaging.

The merged APK manifest was inspected to confirm INTERNET and ACCESS_NETWORK_STATE are absent. The final APK checksum is in `site/dist/downloads/SHA256SUMS.txt`. The app has no seeded payment history, subscriptions or fictional fraud list.

No Android device was attached. Camera focus/scanning, system photo selection and share sheets still require physical-device testing. This build deliberately has no SMS or notification permission prompts. A successful build and JVM tests do not establish all device behaviors.

## Remaining production work

The beta does not include an authoritative fraud feed, physical QR tamper detection, on-device LLM inference, automated dispute filing, store release signing or a public app-store listing. These limitations are disclosed in both the app and website.

## 10 October 2026 — beta 0.3 and Firebase launch

- Google authentication SDK integrated with Android Credential Manager. Google provider deployed in `paydar-hydra`; the installed beta SHA-1 is registered. Google OAuth configuration was downloaded from Firebase.
- Kotlin upgraded to 2.4.0 with the Compose compiler plugin; Android Gradle Plugin upgraded to 8.5.2 for Firebase SDK compatibility.
- `testDebugUnitTest assembleDebug`: successful. 45 tests passed, zero failures/errors. Four website controller tests passed; JavaScript syntax checks passed.
- APK signature verified. Package `com.paydar.app`, version 0.4.0-beta / code 4, minimum SDK 26. SMS, notification and notification-listener permissions are absent from the merged manifest.
- Added stronger 3D rotation/depth during scroll, scroll-linked panel motion, responsive type/spacing and a Payments/account section. Motion preference changes are respected.
- Google sign-in, cancellation and deletion on a physical phone remain unverified. No device is connected. Recent browser permission denial still blocks visual responsive QA; source/controller checks are not a substitute for screenshots.
- Original earlier screenshots document an earlier version, not this release.

Deployment completed at https://paydar.web.app. HTTP 200 confirmed the updated motion module, Payments section and beta 0.3 label. Public GitHub repository and release assets were verified. Firebase Spark rejects APK uploads, so Hosting redirects the download path to the GitHub Release. The published checksum matches the local APK.

## 10 October 2026 — walkthrough layout correction

User screenshot showed the stage controls crossing the caption metadata. Replaced absolutely positioned, fixed-height captions with overlapping CSS grid items that contribute their natural height. Added explicit separation before controls and a static manual walkthrough for short viewports. Added a visible release strip and contrasting product-update section, versioned app styles/scripts and revalidating Hosting cache headers.

README now includes a branded SVG cover and five Mermaid diagrams: system architecture, QR checking, transaction import, authentication and deployment. Current visual browser QA remains blocked by the existing saved permission setting; the user's screenshot is the visual evidence for this correction.
