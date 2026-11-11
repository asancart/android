# Asancart for Android

Open-source SMS listener for [Asancart](https://github.com/asancart) (آسان‌کارت). It reads bank deposit SMS on the shop owner's phone, parses them on the device, and reports each deposit (amount, time, card, and a reference when the bank needs one) to Asancart or any backend that implements the Device API. Account balance and SMS text never leave the phone.

Status: design. No app code yet.

## Design

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

[Apache-2.0](LICENSE). The Asancart name and logo are not covered by this licence; see [asancart/brand](https://github.com/asancart/brand).
