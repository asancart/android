/*
  Wireframe annotations, one entry per screen. English; the app UI is Persian.
  REQ ids point at asancart/documents (components/android-app.md, api/device-api.md, 03_flows.md).
*/
window.ACNotes = {
  welcome: {
    job: 'Say what the app does and what leaves the phone, before asking for anything.',
    does: [
      'Shown on first launch only.',
      'Tapping Start creates the default profile at once. The user never sees an empty app.',
      'Three promises: only amount and time leave the phone, the code is open source, any compatible service works.',
    ],
    reqs: ['DEC-003 only parsed fields leave the device', 'DEC-008 open source', 'DEC-010 several backends'],
  },
  profile: {
    job: 'Pick where the default profile connects. AsanCart is preselected and already configured.',
    does: [
      'A profile is one service connection: base URL, credential, shop, and its card listeners.',
      'AsanCart option carries the platform API address and template source, nothing to type.',
      '"Other service" asks for a base URL (https only). The QR code of that service is scanned next.',
      'Profile name is editable; default "پروفایل اصلی".',
      'Same screen is reused for "New profile" from Home.',
    ],
    reqs: ['REQ-APP-001 API targets', 'DEC-010 Device API is a public contract', 'REQ-FLOW-030 several shops and targets'],
    open: ['Platform API host is a placeholder (api.asancart.ir); docs do not name it yet.'],
  },
  connect: {
    job: 'Connect the profile to the shop account by scanning the dashboard QR code.',
    does: [
      'Prototype: tap the camera area to simulate a scan.',
      'Camera permission is asked here, only when scanning.',
      'QR holds base URL, one-time pairing code and the shop\'s verified cards (F-04).',
      'Device name is sent as device_name and is what the dashboard shows. Prefilled with the phone model.',
      'Fallback to typing the code by hand.',
    ],
    reqs: ['F-04 Device pairing', 'REQ-DEV-001 POST /v1/device/pair'],
  },
  'connect-manual': {
    job: 'Pair without the camera: same call, typed code.',
    does: [
      'Address is fixed for AsanCart, editable for another service.',
      'Code is single use, 10-minute TTL, case-insensitive.',
      'Prototype: clear the code or type 0000 to see the PAIRING_INVALID message.',
    ],
    reqs: ['REQ-DEV-001', 'Error PAIRING_INVALID (422)'],
  },
  connected: {
    job: 'Confirm what got linked and show the cards that came with the pairing.',
    does: [
      'Shows shop name, service host, device name.',
      'Lists the VERIFIED cards from the pair response. These become the card listeners to set up.',
      'The device credential is stored; never shown again.',
    ],
    reqs: ['REQ-DEV-001 response: shop, cards, banks', 'F-04 step 3'],
    open: ['Cards verified after pairing: the Device API has no call to fetch them. Re-pair, or add GET /v1/device/cards.'],
  },
  checklist: {
    job: 'One place that drives the rest of setup: listeners, permissions, turn on and test.',
    does: [
      'Three steps in a fixed order; each ticks when done; progress bar on top.',
      'Step 1 lists every card with "ready" or "not set". Tap a card to set it up.',
      'Step 2 counts the three required permissions.',
      'Step 3 is locked until at least one listener and the SMS permissions exist.',
      '"Later" goes Home; the profile card there shows "setup unfinished" with a Continue button.',
    ],
    reqs: ['REQ-APP-002 Card Bindings', 'F-04 step 4'],
  },
  listener: {
    job: 'Tie one card to its bank SMS sender and parser template, with proof it works.',
    does: [
      'Sender list comes from the phone\'s inbox, filtered by the bank\'s official sender_patterns, with counts. The usual case is one tap.',
      'Template is auto-picked from the official templates for that bank; can switch to another or a custom one.',
      'Live check runs the chosen template on the latest SMS from that sender, on the phone, and shows what would be sent and what never leaves.',
      'Several same-bank cards whose SMS cannot be told apart share one listener with several Card Keys (shown only when the shop has them).',
    ],
    reqs: ['REQ-APP-002', 'REQ-APP-003', 'REQ-DEV-051 sender normalization', 'REQ-DEV-053 template order', 'DEC-003'],
  },
  permissions: {
    job: 'Get every permission the app needs to work in the background, each explained in one line.',
    does: [
      'Required: receive SMS, read SMS (inbox re-scan on start), no battery restriction.',
      'Recommended: notifications (queue stuck, unparsed SMS).',
      'Vendor autostart (Xiaomi, Huawei, Oppo): the app cannot read it, so the user opens settings and confirms.',
      'Each row shows granted / required / recommended; buttons open the system prompt or settings page.',
    ],
    reqs: ['REQ-APP-010 manifest SMS_RECEIVED receiver', 'REQ-APP-011 inbox re-scan'],
  },
  activate: {
    job: 'Turn the profile on and prove the phone reaches the service.',
    does: [
      'Switch: on = listen and send, off = ignore SMS and send nothing. Disabled with the reasons listed (and linked) until prerequisites exist.',
      'Test sends one heartbeat now. last_seen_at updates, so the dashboard shows the phone online. Result shows time and latency, or the exact error.',
      'Prototype: tick "Simulate no connection" above the phone to see the failure.',
      'Paste-an-SMS test runs the templates on the phone only; nothing is sent.',
      'The test never sends a fake Transfer Report (it would create a junk unmatched report).',
    ],
    reqs: ['REQ-DEV-003 heartbeat', 'REQ-DEV-032 last_seen_at', 'dashboard-api Devices: last_seen_at, silent'],
    open: ['If periodic heartbeat is off, the test sends interval_sec = 0, which also tells the server monitoring is off. Fine, but worth stating in the Device API.'],
  },
  home: {
    job: 'At a glance: is every turned-on profile working, and is anything waiting for me?',
    does: [
      'Top banner: all good, or the first profile that cannot send.',
      'Unparsed SMS banner with count, opens the Unparsed tab.',
      'One card per profile: on/off switch, queue length, last SMS, last signal, listener chips, last error.',
      '"New profile" adds another shop or service.',
      'Sample: second profile points at a custom service that returns 503, so its queue is growing.',
    ],
    reqs: ['REQ-APP-014 queue state per target', 'REQ-APP-015 unparsed list'],
  },
  'profile-detail': {
    job: 'Everything about one profile, editable.',
    does: [
      'On/off, connection details, test signal.',
      'Card listeners; tap to edit sender or template.',
      'Send queue: pending, sent today, last send, last error, send now.',
      'Periodic heartbeat on/off and interval (1 min to 1 hour). Missing 3 in a row marks the phone silent in the dashboard.',
      'Remove profile: warns about queued reports that will be lost and that the dashboard still lists the device until revoked.',
    ],
    reqs: ['REQ-APP-004 heartbeat per device', 'REQ-APP-013 persistent queue', 'REQ-DEV-020 silent after 3 intervals'],
    open: ['No Device API call to unpair; removing in the app leaves the device in the dashboard until the shop revokes it.'],
  },
  activity: {
    job: 'What was sent, what is waiting, and what could not be read.',
    does: [
      'Sent: amount, card, time, profile, server result (accepted / duplicate / rejected with reason).',
      'Queue: items still waiting, with the target\'s last error.',
      'Unparsed: SMS from a bound sender that no template read. Never dropped silently.',
      'The phone is never told whether a report matched a payment; "accepted" only means stored.',
    ],
    reqs: ['REQ-DEV-002 results', 'REQ-DEV-011 removal rules', 'REQ-APP-015'],
  },
  'sms-detail': {
    job: 'Explain why one SMS was not read and offer the next step.',
    does: [
      'Shows the full SMS text, local only, with a clear note that it was not sent.',
      'Reason in plain words plus the templates that were tried.',
      'Actions: refresh templates and retry, make a custom template from it, dismiss as not a deposit.',
    ],
    reqs: ['REQ-DEV-052 statuses', 'REQ-DEV-056 outcomes', 'DEC-014 custom templates'],
  },
  templates: {
    job: 'See which parser templates are on the phone and add custom ones.',
    does: [
      'Official templates are fetched from the service with ?since=server_time; status line shows the last check.',
      'Each row: layout name, bank, version, deposit or ignore template.',
      'Custom templates live only on this phone.',
    ],
    reqs: ['REQ-DEV-004..007', 'DEC-014'],
  },
  'template-edit': {
    job: 'Write a custom template in the same schema v1 as official ones, and test it.',
    does: [
      'Fields map 1:1 to schema v1: name, sender_patterns, pattern, direction, amount_unit, priority.',
      'Test runs the template on a pasted SMS and shows the captured groups.',
      'Prototype uses a Blu bank sample.',
    ],
    reqs: ['REQ-DEV-040..042 schema', 'REQ-APP-005'],
  },
  'app-settings': {
    job: 'App-wide settings and the privacy statement.',
    does: [
      'Theme: light, dark, system (this switch also changes the prototype phone).',
      'Permissions shortcut with status, automatic template refresh.',
      'Plain list of what is sent and what never leaves the phone.',
      'Version, source code link, licence.',
    ],
    reqs: ['REQ-BRAND-005 themes', 'DEC-003', 'DEC-008'],
  },
};
