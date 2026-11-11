# Design brief: Android app

Status: design approved for prototyping 2026-11-11. Source of product rules: asancart/documents `components/android-app.md` (REQ-APP-*), `api/device-api.md` (REQ-DEV-*), `03_flows.md` F-04. This file records the UX decisions on top of them.

## Scope

- Read bank SMS, parse on the device, send Transfer Reports to one or more services. Nothing else (REQ-APP-000).
- UI: Persian, RTL. Amounts in rials with Persian digits. Card numbers, codes, URLs LTR.
- Look: Asancart C1 "Saffron & Plum" tokens, Lalezar display + Vazirmatn body, Material 3 structure. Light, dark, system.

## Terms

| Term | Meaning |
|---|---|
| Profile | One service connection: base URL, device credential, shop, card listeners, heartbeat setting, on/off. Maps to an API target (REQ-APP-001) |
| Card listener | UI name for a Card Binding (REQ-APP-002): card(s) + SMS sender + parser template, inside one profile |
| Signal | UI name for a heartbeat (REQ-DEV-003) |

A card bound to several targets (REQ-APP-002) = the same card with a listener in several profiles.

## Owner decisions

- D1. First launch creates a default profile immediately. No empty state.
- D2. Profile setup offers Asancart (preselected, API addresses built in) or another service (base URL typed by the user).
- D3. Connect by scanning the dashboard QR code.
- D4. After connecting, the user can easily: set up card listeners, activate the profile, confirm every permission.
- D5. Activate = per-profile switch. On: listen and send. Off: ignore SMS, send nothing.
- D6. A test button sends a signal so the phone shows in the dashboard.

## My calls

- C1. Setup checklist screen after pairing: 1 card listeners, 2 permissions, 3 turn on and test. Ticks per step. "Later" allowed; Home shows "setup unfinished" with Continue.
- C2. Switch is blocked (with linked reasons) until ≥ 1 listener and SMS receive + read permissions exist.
- C3. Test signal = one `POST /v1/device/heartbeat` now. Shows time and latency or the exact error. Never sends a fake Transfer Report.
- C4. Paste-an-SMS test on the device only, nothing sent.
- C5. Manual code entry fallback; device name prefilled with phone model, editable.
- C6. Sender suggestions: inbox senders that match the bank's official `sender_patterns`, with 30-day counts. Template auto-picked from the bank's official templates. Live check on the latest SMS from that sender shows what is sent and what is not.
- C7. Permissions: required = receive SMS, read SMS, battery optimization exemption. Recommended = notifications, vendor autostart (user confirms; not readable). Camera asked only at QR scan.
- C8. Navigation: Home, Activity (sent / queue / unparsed), Templates, Settings.
- C9. Home: one card per profile with switch, queue length, time since last SMS and last signal, listener chips, last error. Banners for a failing profile and unparsed SMS.
- C10. Unparsed SMS detail: local-only notice, reason in plain words, templates tried, actions: refresh and retry, make custom template, dismiss.
- C11. Platform API host shown as `api.asancart.ir` (placeholder).

## Screens

| # | Id | Stage | Name |
|---|---|---|---|
| 1 | welcome | First run | Welcome |
| 2 | profile | First run | Default profile / new profile |
| 3 | connect | First run | Scan QR |
| 4 | connect-manual | First run | Enter code by hand |
| 5 | connected | First run | Connected |
| 6 | checklist | Setup | Setup checklist |
| 7 | listener | Setup | Card listener |
| 8 | permissions | Setup | Permissions |
| 9 | activate | Setup | Turn on and test |
| 10 | home | Daily use | Home |
| 11 | profile-detail | Daily use | Profile |
| 12 | activity | Daily use | Activity |
| 13 | sms-detail | Daily use | Unparsed SMS |
| 14 | templates | Daily use | Templates |
| 15 | template-edit | Daily use | Custom template |
| 16 | app-settings | Daily use | Settings |

Per-screen behaviour and spec links: `design/prototype/notes.js` (shown in the wireframe).

## Open points

| Id | Point | Proposal |
|---|---|---|
| O1 | Cards verified after pairing never reach the device; Pair is the only call that returns cards | Add `GET /v1/device/cards`, or require re-pairing |
| O2 | No device-side unpair; removing a profile leaves the device listed until the shop revokes it | Add `POST /v1/device/unpair`, or keep the warning text |
| O3 | Platform API host not named in the docs | Decide the host |
| O4 | Test signal with periodic heartbeat off sends `interval_sec = 0`, which also tells the server monitoring is off | State it in REQ-DEV-003 |

## Not in this design

- App code, Android project setup, store listing.
- Push notifications to the shop (SMS only, DEC-001).
