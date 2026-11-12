# Agent notes (android repo)

- Product rules: Asancart documents `components/android-app.md` (REQ-APP-*), `api/device-api.md` (REQ-DEV-*), `12_operations.md` REQ-OPS-010..013, DEC-003, DEC-008, DEC-010. Docs win over code; raise or propose a docs fix when they disagree.
- App identity is fixed (DEC-035, REQ-APP-042): application ID and Kotlin package `io.github.asancart.android`, debug suffix `.debug`, Apache-2.0, minSdk 24. Never change them; CI checks the APKs with aapt2.
- Gates before review: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`, CI green, screenshots in `app/build/outputs/screenshots/` looked at against `design/mockups.html`.
- No Google Play Services or Firebase (REQ-APP-041). The build fails on such dependencies, and CI runs `scripts/check-manifest-no-gms.sh` on the debug and release APK manifests, which catches library entries that reach Play Services at runtime, such as emoji2's font initializer (removed in `AndroidManifest.xml`). No networking or API-connection code while BLK-001 holds: use `StubDeviceApi`.
- Never log or `toString` SMS text, balances, account digits or credentials (REQ-OPS-003, REQ-OPS-011). Log through `AppLog` with ids, names and counts; `LogRedactor` is the backstop and `LogRedactorTest` must cover every new secret type.
- UI: Persian RTL, strings in `res/values/strings.xml`, rials only, Jalali dates, tokens from `ui/theme` only, no developer text in UI copy.
- Never commit a release keystore or any secret. Commits signed, no AI attribution.
