# Asancart for Android

Open-source SMS listener for [Asancart](https://github.com/asancart) (آسان‌کارت). It reads bank deposit SMS on the shop owner's phone, parses them on the device, and reports each deposit (amount, time, card, and a reference when the bank needs one) to Asancart or any backend that implements the Device API. Account balance and SMS text never leave the phone.

Status: app scaffold. Kotlin and Jetpack Compose, Persian right-to-left UI, Asancart theme, first screens (welcome, home with the default profile, activity, templates, settings). SMS capture and parsing, pairing, the send queue and logging come next. The app makes no network calls yet.

## Build

App identity (DEC-035): application ID and Kotlin package `io.github.asancart.android` (debug builds add `.debug`), Apache-2.0, minSdk 24 (Android 7.0). The ID is fixed from the first published APK.


Needs JDK 21 (17 or newer works), the Android SDK with platform 37, and access to Google's Maven repository.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The Platform address is a build setting, not code: release builds use `https://api.asancart.org`, debug builds a local backend, by default `http://10.0.2.2:8080` (the dev machine as seen from the Android emulator). Change it with `-Pasancart.platformApiUrl=<url>` or in `~/.gradle/gradle.properties`.

The debug APK lands in `app/build/outputs/apk/debug/`, the unsigned release APK in `app/build/outputs/apk/release/`. Screen captures from the unit tests land in `app/build/outputs/screenshots/`. CI ([`.github/workflows/android.yml`](.github/workflows/android.yml)) runs the same tasks on every pull request and uploads the signed debug APK and the screenshots as artifacts.

## Layout

| Path | What |
|---|---|
| [`app/`](app) | The Android app: Compose UI, theme, Android adapters |
| [`core/`](core) | Plain Kotlin, no Android: Profiles, Parser Templates, normalization, Transfer Reports, queue, heartbeat, Device API contract and its stub. Runs as JVM unit tests |
| [`design/`](design) | Clickable wireframe and full mockups |
| [`docs/design-brief.md`](docs/design-brief.md) | UX decisions, screen list |

Packages:

| Package | Role |
|---|---|
| `core.profile` | Profiles, API targets, Card Bindings, the rule for turning a profile on |
| `core.sms`, `core.template` | Incoming SMS, normalization and Parser Template engine (schema v1) |
| `core.queue`, `core.heartbeat` | Transfer Reports, the per-target outgoing queue, heartbeat setting |
| `core.deviceapi` | Typed Device API (`/v1/device`) and an in-memory stub |
| `capture` | SMS receiver and inbox re-scan (Android side) |
| `log` | Rolling file log (5 × 1 MB), the log filter, log export, ACRA crash reports; never SMS text, balances or credentials |
| `ui` | Screens, components and the theme (`ui/theme`, colours copied from the Asancart tokens) |

## Rules

- No Google Play Services or Firebase. The build fails if any dependency pulls one in.
- SMS text, balances, account digits and credentials stay on the phone and out of logs: every log line, field and crash trace goes through `LogRedactor`.
- Logs stay on the phone; Settings → Logs shares the newest 1 MB as one text file. Crash reports (ACRA) go only to Profiles whose user turned them on.
- UI copy is Persian, in `res/values/strings.xml`; colours, fonts and radii come from the Asancart tokens only.

## Design

View online: **[asancart.github.io/android](https://asancart.github.io/android/)** ([wireframe](https://asancart.github.io/android/wireframe.html), [full design](https://asancart.github.io/android/mockups.html)).

| Path | What |
|---|---|
| [`design/wireframe.html`](design/wireframe.html) | Grayscale clickable wireframe with notes per screen: what it does, which spec it follows, open points |
| [`design/mockups.html`](design/mockups.html) | Full design: every screen in light and dark, plus a clickable phone |
| [`design/prototype/`](design/prototype) | Shared screen code, sample data, tokens. Both pages render from it |
| [`docs/design-brief.md`](docs/design-brief.md) | Decisions, screen list, open points |

Open either HTML file straight from a checkout. `node design/build.mjs` writes self-contained copies to `design/dist/`.

## How it works

1. Install, tap Start. A default profile is created at once.
2. Pick the service: Asancart (preset) or another compatible service.
3. Scan the QR code from the shop dashboard (or type the code).
4. Set up a listener per card (bank SMS sender and parser template), grant permissions.
5. Turn the profile on and send a test signal. The phone shows as online in the dashboard.

## Specs

Product rules live in the Asancart documents: Android app (`REQ-APP-*`), Device API (`/v1/device`: pair, reports, heartbeat, parser templates), device pairing flow F-04, brand tokens.

## Licence

The app is open source under [Apache-2.0](LICENSE). The Asancart name and logo are not covered by this licence; see [asancart/brand](https://github.com/asancart/brand). Bundled fonts are under the SIL Open Font License: see [NOTICE](NOTICE).
