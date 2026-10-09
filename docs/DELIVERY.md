# PayDar delivery — beta 0.3.0

- Website: https://paydar.web.app
- Public source: https://github.com/hydra-eng/paydar
- Android release: https://github.com/hydra-eng/paydar/releases/tag/v0.3.0-beta
- Firebase project: paydar-hydra; Hosting site: paydar.
- APK: 18,972,498 bytes; SHA-256 587283c410154e20dd51a6d6c6ff114fd294851c0df1c66ea27d8ac92e606d40.
- Signing certificate matches beta 0.2. Google provider enabled; debug SHA-1 registered.
- Android: 45 tests passed and build successful. Website: 4 controller tests passed. Production HTTP response and download route verified.
- Physical-device authentication, account deletion and notification tests remain pending. Saved browser permission prevents current visual QA.

The Firebase Spark plan does not allow APK uploads. Hosting redirects the download route to the public GitHub Release asset. No billing upgrade was made.

The public source checkout is in the sibling `paydar-public` directory. The original `paydar/site` managed Sites checkout remains intact. Old source archives and screenshots document earlier beta versions; use the GitHub repository and release above for the current delivery.
