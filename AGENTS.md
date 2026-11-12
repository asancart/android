# Agent notes (android repo)

- Product rules: Asancart documents `components/android-app.md` (REQ-APP-*), `api/device-api.md` (REQ-DEV-*), `12_operations.md` REQ-OPS-010..013, DEC-003, DEC-008, DEC-010. Docs win over code; raise or propose a docs fix when they disagree.
- Gates before review: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, CI green, screenshots in `app/build/outputs/screenshots/` looked at against `design/mockups.html`.
- No Google Play Services or Firebase (REQ-APP-041). The build fails on such dependencies, and CI runs `scripts/check-manifest-no-gms.sh` on the debug APK manifest and the merged release manifest (`:app:processReleaseManifest`), which catches library entries that reach Play Services at runtime, such as emoji2's font initializer (removed in `AndroidManifest.xml`). No networking or API-connection code while BLK-001 holds: use `StubDeviceApi`.
- Never log or `toString` SMS text, balances, account digits or credentials (REQ-OPS-003, REQ-OPS-011).
- UI: Persian RTL, strings in `res/values/strings.xml`, rials only, Jalali dates, tokens from `ui/theme` only, no developer text in UI copy.
- Never commit a release keystore or any secret. Commits signed, no AI attribution.
