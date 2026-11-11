/*
  Asancart Android app: clickable design prototype.
  One screen set, two skins: "brand" (full look) and "wireframe" (grayscale, same layout).
  Sample data only. No network, no storage.

  API (window.ACProto):
    const p = ACProto.create({ skin, theme, preset, screen, onChange, onTheme });
    p.mount(element);            // interactive phone
    p.jump(screenId);            // jump with that screen's sample state
    p.reset();                   // back to first run
    p.setTheme('light'|'dark'|'system');
    p.setFlag('offline', bool);  // next connection test fails
    ACProto.renderStatic(screenId, { skin, theme }) -> HTML string of a non-interactive phone
    ACProto.SCREENS, ACProto.ORDER
*/
(function (global) {
  'use strict';

  /* ---------- formatting ---------- */
  const PD = '۰۱۲۳۴۵۶۷۸۹';
  const fd = (s) => String(s).replace(/\d/g, (d) => PD[d]);
  const fn = (n) => Number(n).toLocaleString('fa-IR');
  const rial = (n) => fn(n) + ' ریال';
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const ltr = (s, cls = '') => `<span class="ltr ${cls}">${esc(s)}</span>`;

  /* ---------- icons (24px, stroke) ---------- */
  const ICONS = {
    back: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    chev: '<path d="M15 6l-6 6 6 6"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>',
    qr: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2"/>',
    keyboard: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M7 10h.01M11 10h.01M15 10h.01M7 14h10"/>',
    sms: '<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9.5h8M8 12.5h5"/>',
    card: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M3 10h18M7 15h4"/>',
    shield: '<path d="M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z"/><path d="M8.5 12l2.5 2.5 4.5-5"/>',
    bell: '<path d="M6 16V11a6 6 0 1112 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 004 0"/>',
    battery: '<rect x="3" y="7" width="16" height="10" rx="2"/><path d="M21 10.5v3M7 10v4M10.5 10v4"/>',
    power: '<path d="M12 3v8"/><path d="M7 6.5a7 7 0 1010 0"/>',
    signal: '<circle cx="12" cy="12" r="2"/><path d="M8 8a5.7 5.7 0 000 8M16 8a5.7 5.7 0 010 8M5.2 5.2a9.7 9.7 0 000 13.6M18.8 5.2a9.7 9.7 0 010 13.6"/>',
    refresh: '<path d="M20 11a8 8 0 10-2.3 5.7"/><path d="M20 4v7h-7"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    sliders: '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
    home: '<path d="M4 11l8-7 8 7v9h-5v-6H9v6H4z"/>',
    activity: '<path d="M3 12h4l3-7 4 14 3-7h4"/>',
    doc: '<path d="M7 3h7l5 5v13H7z"/><path d="M14 3v5h5M10 13h6M10 17h6"/>',
    warn: '<path d="M12 4l9 16H3z"/><path d="M12 10v4M12 17h.01"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5h.01"/>',
    trash: '<path d="M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13"/>',
    edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
    flask: '<path d="M9 3h6M10 3v6l-5 9a2 2 0 002 3h10a2 2 0 002-3l-5-9V3"/><path d="M7.5 15h9"/>',
    offline: '<path d="M3 3l18 18"/><path d="M7 18h10a4 4 0 001.5-7.7M15.5 7.4A6 6 0 006 11a3.5 3.5 0 001 6.9"/>',
    phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/>',
    link: '<path d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1"/>',
    code: '<path d="M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16"/>',
    moon: '<path d="M20 14.5A8 8 0 119.5 4a6.5 6.5 0 0010.5 10.5z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
    auto: '<circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 010 16z" fill="currentColor"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 018 0v3"/>',
    send: '<path d="M20 4L3 11l7 2.5L12.5 21z"/><path d="M10 13.5L20 4"/>',
    eyeoff: '<path d="M3 3l18 18M10.6 6.1A9.8 9.8 0 0112 6c5 0 8.5 4.5 9 6-.3.9-1.3 2.5-2.9 3.9M6.6 7.6C4.6 8.9 3.4 10.9 3 12c.5 1.5 4 6 9 6 1.6 0 3-.4 4.2-1.1"/><path d="M9.9 9.9a3 3 0 004.2 4.2"/>',
    inbox: '<path d="M3 13l3-8h12l3 8v6H3z"/><path d="M3 13h5l1.5 2.5h5L16 13h5"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    camera: '<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/>',
  };
  const ic = (name, cls = '') => `<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ''}</svg>`;

  /* Logo: split-duo mark, fills from tokens (asancart/brand logo/svg/asancart-mark-css.svg) */
  const LOGO = '<svg viewBox="40 110 432 290" role="img" aria-label="آسان‌کارت"><path fill="var(--ac-primary)" fill-rule="evenodd" d="M75.56,123.79 73.37,124.15 71.2,124.66 69.07,125.3 66.99,126.09 64.96,127.01 63,128.06 61.11,129.23 59.3,130.53 57.58,131.94 55.96,133.46 54.44,135.08 53.03,136.8 51.73,138.61 50.56,140.5 49.51,142.46 48.59,144.49 47.8,146.57 47.16,148.7 46.65,150.87 46.29,153.06 46.07,155.28 46,157.5 46,354.5 46.07,356.72 46.29,358.94 46.65,361.13 47.16,363.3 47.8,365.43 48.59,367.51 49.51,369.54 50.56,371.5 51.73,373.39 53.03,375.2 54.44,376.92 55.96,378.54 57.58,380.06 59.3,381.47 61.11,382.77 63,383.94 64.96,384.99 66.99,385.91 69.07,386.7 71.2,387.34 73.37,387.85 75.56,388.21 77.78,388.43 80,388.5 236.04,388.5 268.73,275 215.2,275 254.52,123.5 80,123.5 77.78,123.57ZM166,189.67 142,189.67 142,194.67 166,194.67 166,210.33 142,210.33 142,215.33 166,215.33 166,220.5 165.97,221.35 165.89,222.2 165.75,223.04 165.56,223.86 165.31,224.68 165.01,225.47 164.66,226.25 164.26,227 163.81,227.72 163.31,228.41 162.77,229.07 162.19,229.69 161.57,230.27 160.91,230.81 160.22,231.31 159.5,231.76 158.75,232.16 157.97,232.51 157.18,232.81 156.36,233.06 155.54,233.25 154.7,233.39 153.85,233.47 153,233.5 128.5,233.5 128.5,171.5 153,171.5 153.85,171.53 154.7,171.61 155.54,171.75 156.36,171.94 157.18,172.19 157.97,172.49 158.75,172.84 159.5,173.24 160.22,173.69 160.91,174.19 161.57,174.73 162.19,175.31 162.77,175.93 163.31,176.59 163.81,177.28 164.26,178 164.66,178.75 165.01,179.53 165.31,180.32 165.56,181.14 165.75,181.96 165.89,182.8 165.97,183.65 166,184.5ZM86,215.33 110,215.33 110,210.33 86,210.33 86,194.67 110,194.67 110,189.67 86,189.67 86,184.5 86.03,183.65 86.11,182.8 86.25,181.96 86.44,181.14 86.69,180.32 86.99,179.53 87.34,178.75 87.74,178 88.19,177.28 88.69,176.59 89.23,175.93 89.81,175.31 90.43,174.73 91.09,174.19 91.78,173.69 92.5,173.24 93.25,172.84 94.03,172.49 94.82,172.19 95.64,171.94 96.46,171.75 97.3,171.61 98.15,171.53 99,171.5 123.5,171.5 123.5,233.5 99,233.5 98.15,233.47 97.3,233.39 96.46,233.25 95.64,233.06 94.82,232.81 94.03,232.51 93.25,232.16 92.5,231.76 91.78,231.31 91.09,230.81 90.43,230.27 89.81,229.69 89.23,229.07 88.69,228.41 88.19,227.72 87.74,227 87.34,226.25 86.99,225.47 86.69,224.68 86.44,223.86 86.25,223.04 86.11,222.2 86.03,221.35 86,220.5Z"/><path fill="var(--ac-accent)" fill-rule="evenodd" d="M434.22,388.43 436.44,388.21 438.63,387.85 440.8,387.34 442.93,386.7 445.01,385.91 447.04,384.99 449,383.94 450.89,382.77 452.7,381.47 454.42,380.06 456.04,378.54 457.56,376.92 458.97,375.2 460.27,373.39 461.44,371.5 462.49,369.54 463.41,367.51 464.2,365.43 464.84,363.3 465.35,361.13 465.71,358.94 465.93,356.72 466,354.5 466,157.5 465.93,155.28 465.71,153.06 465.35,150.87 464.84,148.7 464.2,146.57 463.41,144.49 462.49,142.46 461.44,140.5 460.27,138.61 458.97,136.8 457.56,135.08 456.04,133.46 454.42,131.94 452.7,130.53 450.89,129.23 449,128.06 447.04,127.01 445.01,126.09 442.93,125.3 440.8,124.66 438.63,124.15 436.44,123.79 434.22,123.57 432,123.5 281.38,123.5 248.8,249 303.27,249 263.1,388.5 432,388.5Z"/></svg>';

  /* ---------- sample data ---------- */
  const SHOP = { name: 'کافه نارنج' };
  const ASANCART_URL = 'https://api.asancart.ir/v1/device';
  const CARDS = {
    c1: { id: 'c1', bank: 'melli', bankName: 'بانک ملی', short: 'ملی', masked: '6037 99•• •••• 4821', last4: '4821' },
    c2: { id: 'c2', bank: 'mellat', bankName: 'بانک ملت', short: 'ملت', masked: '6104 33•• •••• 1907', last4: '1907' },
  };
  const SENDERS = {
    melli: [
      { id: '700717', count: 12, note: 'واریز و برداشت', suggested: true },
      { id: '+98700717', count: 3, note: 'واریز و برداشت' },
      { id: 'Bank Melli', count: 5, note: 'فقط رمز یک‌بار مصرف' },
    ],
    mellat: [
      { id: 'Bank Mellat', count: 18, note: 'واریز، برداشت و هشدار', suggested: true },
      { id: '6104', count: 1, note: 'سود سپرده' },
    ],
  };
  const TEMPLATES = [
    { id: 't1', name: 'melli-deposit', bank: 'melli', bankName: 'بانک ملی', v: 3, dir: 'in' },
    { id: 't2', name: 'melli-other-ignore', bank: 'melli', bankName: 'بانک ملی', v: 1, dir: 'out' },
    { id: 't3', name: 'mellat-deposit', bank: 'mellat', bankName: 'بانک ملت', v: 2, dir: 'in' },
    { id: 't4', name: 'mellat-alert-ignore', bank: 'mellat', bankName: 'بانک ملت', v: 1, dir: 'out' },
    { id: 't5', name: 'saderat-deposit', bank: 'saderat', bankName: 'بانک صادرات', v: 2, dir: 'in' },
    { id: 't6', name: 'tejarat-deposit', bank: 'tejarat', bankName: 'بانک تجارت', v: 1, dir: 'in' },
    { id: 't7', name: 'saman-deposit', bank: 'saman', bankName: 'بانک سامان', v: 1, dir: 'in' },
    { id: 't8', name: 'pasargad-deposit', bank: 'pasargad', bankName: 'بانک پاسارگاد', v: 1, dir: 'in' },
  ];
  const DEFAULT_TPL = { melli: 't1', mellat: 't3' };
  const LAST_SMS = {
    melli: { text: 'بانك ملي ايران\nانتقال:25,000,000+\nحساب:10000\nمانده:198,088,329\n0820-14:21', at: 'امروز ۱۴:۲۱', amount: 25000000 },
    mellat: { text: 'حساب8065955489\nواریز2,250,000\nمانده23,236,833\n05/08/20-13:55', at: 'امروز ۱۳:۵۵', amount: 2250000 },
  };
  const SAMPLE_PASTE = 'حساب8065955489\nواریز3,200,118\nمانده26,436,951\n05/08/20-14:30';
  const CUSTOM_SAMPLE = 'بلو\nواریز ۱٬۰۰۰٬۰۰۰٬۰۰۰ ریال به حساب شما\nموجودی: ۲٬۴۰۰٬۰۰۰٬۰۰۰ ریال\n۱۴۰۵.۰۸.۲۰ ۱۱:۰۲';
  const CUSTOM_PATTERN = '^بلو\\nواریز (?<amount>[\\d,]+) ریال به حساب شما\\nموجودی: [\\d,]+ ریال\\n\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}$';

  const REPORTS = [
    { amount: 4850270, card: 'c1', at: '۱۴:۲۱', profile: 'p1', result: 'ACCEPTED' },
    { amount: 3200118, card: 'c2', at: '۱۳:۵۵', profile: 'p1', result: 'ACCEPTED' },
    { amount: 1250034, card: 'c1', at: '۱۲:۴۰', profile: 'p1', result: 'DUPLICATE' },
    { amount: 780512, card: 'c2', at: '۱۱:۰۸', profile: 'p1', result: 'ACCEPTED' },
    { amount: 2000000, card: 'c1', at: '۰۹:۴۷', profile: 'p1', result: 'REJECTED', reason: 'این کارت در داشبورد لغو شده بود.' },
  ];
  const QUEUE = [
    { amount: 6400000, card: 'c2', at: '۱۴:۱۰', profile: 'p2' },
    { amount: 950000, card: 'c2', at: '۱۳:۵۵', profile: 'p2' },
    { amount: 1720400, card: 'c2', at: '۱۲:۰۲', profile: 'p2' },
  ];
  const UNPARSED = [
    { id: 'u1', sender: '700717', card: 'c1', at: 'امروز ۰۹:۱۴', status: 'NO_MATCH',
      text: 'بانك ملي ايران\nانتقال از 6219:5,000,000+\nحساب:10000\nمانده:203,088,329\n0820-09:14',
      reason: 'متن با هیچ‌کدام از قالب‌های بانک ملی جور نشد. احتمالاً بانک شکل پیامک را عوض کرده است.', tried: 'melli-deposit v3، melli-other-ignore v1' },
    { id: 'u2', sender: 'Bank Mellat', card: 'c2', at: 'دیروز ۱۸:۳۰', status: 'DIRECTION_UNKNOWN',
      text: 'حساب8065955489\nواریز شبا2,000,000\nمانده21,236,833\n05/08/19-18:30',
      reason: 'قالب mellat-deposit متن را خواند، ولی نوع تراکنش «واریز شبا» در فهرست واریزها نیست.', tried: 'mellat-deposit v2' },
  ];

  /* ---------- state ---------- */
  const clone = (o) => JSON.parse(JSON.stringify(o));

  function newProfile(id, over) {
    return Object.assign({
      id, name: 'پروفایل اصلی', service: 'asancart', url: ASANCART_URL, connected: false,
      shop: null, deviceName: 'Galaxy A54', enabled: false,
      listeners: {}, heartbeat: { on: true, interval: 300 },
      queue: 0, sentToday: 0, lastSend: null, lastError: null,
      lastSms: null, lastSignal: null, test: null,
    }, over || {});
  }

  function seed(preset) {
    const s = {
      onboarded: false,
      profiles: [newProfile('p1')],
      perms: { receive: false, read: false, battery: false, notif: false, autostart: false },
      autoTemplates: true,
      draft: { name: 'پروفایل اصلی', service: 'asancart', customUrl: '', deviceName: 'Galaxy A54', code: 'K7QM-2XPA', isNew: false, profileId: 'p1' },
      codeErr: false,
      activityTab: 'sent',
      cur: { profile: 'p1', card: 'c1', sms: 'u1' },
      smsSample: SAMPLE_PASTE, smsResult: null,
      tpl: { name: 'blu-deposit', senders: '90000258, +989999987641', pattern: CUSTOM_PATTERN, mode: 'keyword', kw: 'واریز', unit: 'rial', priority: '50', sample: CUSTOM_SAMPLE, result: null },
      unparsed: [],
      reports: [],
      queue: [],
      custom: [],
      tplPick: null,
    };
    const pair = (p) => Object.assign(p, { connected: true, shop: SHOP.name, cards: ['c1', 'c2'] });
    const listen = (p, cid, sender) => { p.listeners[cid] = { sender: sender || SENDERS[CARDS[cid].bank][0].id, tpl: DEFAULT_TPL[CARDS[cid].bank], ok: true }; };
    const p1 = s.profiles[0];

    if (preset === 'fresh') return s;
    pair(p1);
    if (preset === 'paired') return s;
    listen(p1, 'c1');
    s.perms.receive = true; s.perms.read = true;
    if (preset === 'setup-mid') return s;
    listen(p1, 'c2');
    s.perms.battery = true; s.perms.notif = true;
    if (preset === 'ready') return s;
    p1.enabled = true;
    p1.test = { state: 'ok', at: '۱۴:۳۲', ms: 240 };
    p1.lastSignal = '۲ دقیقه';
    if (preset === 'tested') return s;

    // 'daily': a week in
    s.onboarded = true;
    s.perms.autostart = true;
    Object.assign(p1, { sentToday: 5, lastSend: '۱۴:۲۱', lastSms: '۱۱ دقیقه' });
    const p2 = newProfile('p2', {
      name: 'سرویس خودم', service: 'custom', url: 'https://pay.narenj.example/v1/device', connected: true,
      shop: 'فروشگاه دوم نارنج', cards: ['c2'], enabled: true, queue: 3, sentToday: 0,
      lastSend: 'دیروز ۲۲:۴۰', lastSms: '۳۷ دقیقه', lastSignal: '۴۱ دقیقه',
      lastError: 'سرویس پاسخ نمی‌دهد (HTTP 503). تلاش بعدی تا ۲ دقیقه دیگر.',
      heartbeat: { on: false, interval: 0 },
    });
    p2.listeners.c2 = { sender: 'Bank Mellat', tpl: 't3', ok: true };
    s.profiles.push(p2);
    s.unparsed = clone(UNPARSED);
    s.reports = clone(REPORTS);
    s.queue = clone(QUEUE);
    s.custom = [{ id: 'k1', name: 'blu-deposit', bankName: 'بلو', v: 1, dir: 'in' }];
    return s;
  }

  /* ---------- derived ---------- */
  const prof = (s, id) => s.profiles.find((p) => p.id === (id || s.cur.profile)) || s.profiles[0];
  const host = (url) => { try { return new URL(url).host; } catch (e) { return url || '—'; } };
  const tplById = (s, id) => TEMPLATES.find((t) => t.id === id) || s.custom.find((t) => t.id === id);
  const cardsOf = (p) => (p.cards || []).map((c) => CARDS[c]);
  const listenersDone = (p) => cardsOf(p).filter((c) => p.listeners[c.id]).length;
  const REQUIRED_PERMS = ['receive', 'read', 'battery'];
  const permsDone = (s) => REQUIRED_PERMS.filter((k) => s.perms[k]).length;
  function blockers(s, p) {
    const b = [];
    if (!listenersDone(p)) b.push({ t: 'هنوز برای هیچ کارتی شنونده تنظیم نشده.', go: 'checklist' });
    if (!s.perms.receive || !s.perms.read) b.push({ t: 'دسترسی پیامک داده نشده.', go: 'permissions' });
    return b;
  }
  function steps(s, p) {
    const total = cardsOf(p).length;
    const one = total > 0 && listenersDone(p) === total;
    const two = permsDone(s) === REQUIRED_PERMS.length;
    const three = p.enabled && p.test && p.test.state === 'ok';
    return { one, two, three, count: [one, two, three].filter(Boolean).length };
  }

  /* Tiny stand-in for the on-device template engine (REQ-DEV-050..056), enough for the samples. */
  function parseSample(text) {
    const t = String(text || '')
      .replace(/[‎‏؜‪-‮⁦-⁩﻿]/g, '')
      .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک')
      .replace(/[۰-۹]/g, (d) => '0123456789'['۰۱۲۳۴۵۶۷۸۹'.indexOf(d)])
      .replace(/٬/g, ',').trim();
    let m = t.match(/^حساب\d+\nواریز([\d,]+)\nمانده[\d,]+\n\d{2}\/\d{2}\/\d{2}-\d{2}:\d{2}$/);
    if (m) return { status: 'DEPOSIT', amount: +m[1].replace(/,/g, ''), tpl: 'mellat-deposit', v: 2, card: 'c2' };
    m = t.match(/^بانک ملی ایران\nانتقالی?:([\d,]+)\+\nحساب:\d+\nمانده:[\d,]+\n\d{4}-\d{2}:\d{2}$/);
    if (m) return { status: 'DEPOSIT', amount: +m[1].replace(/,/g, ''), tpl: 'melli-deposit', v: 3, card: 'c1' };
    m = t.match(/^بلو\nواریز ([\d,]+) ریال به حساب شما\nموجودی: [\d,]+ ریال\n\d{4}\.\d{2}\.\d{2} \d{2}:\d{2}$/);
    if (m) return { status: 'DEPOSIT', amount: +m[1].replace(/,/g, ''), tpl: 'blu-deposit', v: 1, card: null };
    if (/^حساب\d+\nواریز\S/.test(t)) return { status: 'DIRECTION_UNKNOWN' };
    if (/برداشت/.test(t)) return { status: 'IGNORED' };
    return { status: 'NO_MATCH' };
  }
  const STATUS_FA = {
    NO_MATCH: 'با هیچ قالبی جور نشد',
    DIRECTION_UNKNOWN: 'نوع تراکنش شناخته نشد',
    BAD_AMOUNT: 'مبلغ خوانده نشد',
    IGNORED: 'واریز نیست؛ نادیده گرفته می‌شود',
    DEPOSIT: 'واریز',
  };

  /* ---------- building blocks ---------- */
  const bar = (title, o = {}) => `<header class="abar">${o.back === false ? '<span style="width:10px"></span>' : `<button class="ibtn" data-back aria-label="بازگشت">${ic('back')}</button>`}<h1>${title}</h1>${o.actions || ''}</header>`;
  const bigBar = (title, actions = '') => `<header class="abar big"><h1>${title}</h1>${actions}</header>`;
  const navBar = (on, s) => `<nav class="nav">${[
    ['home', 'home', 'خانه'], ['activity', 'activity', 'فعالیت'], ['templates', 'doc', 'قالب‌ها'], ['app-settings', 'sliders', 'تنظیمات'],
  ].map(([id, icon, label]) => `<button data-tab="${id}" class="${on === id ? 'on' : ''}"><span class="ind">${ic(icon)}${id === 'activity' && s.unparsed.length ? `<span class="badge">${fn(s.unparsed.length)}</span>` : ''}</span>${label}</button>`).join('')}</nav>`;
  const field = (o) => `<div class="fld ${o.err ? 'err' : ''}"><label for="${o.id}">${o.label}</label>${o.area
    ? `<textarea id="${o.id}" class="inp ${o.ltr ? 'ltr' : ''} ${o.mono ? 'mono' : ''}" data-bind="${o.bind}" ${o.rows ? `rows="${o.rows}"` : ''}>${esc(o.value || '')}</textarea>`
    : `<input id="${o.id}" class="inp ${o.ltr ? 'ltr' : ''} ${o.mono ? 'mono' : ''}" data-bind="${o.bind}" value="${esc(o.value || '')}" placeholder="${esc(o.ph || '')}" ${o.ro ? 'readonly' : ''}>`}${o.help ? `<span class="help">${o.err || o.help}</span>` : (o.err ? `<span class="help">${o.err}</span>` : '')}</div>`;
  const sw = (on, act, v, dis) => `<button class="sw ${on ? 'on' : ''}" role="switch" aria-checked="${on}" data-act="${act}" data-v="${v || ''}" ${dis ? 'aria-disabled="true"' : ''}></button>`;
  const mini = (c) => `<span class="minicard ${c.bank}"></span>`;
  const resultPill = (r) => r === 'ACCEPTED' ? '<span class="pill ok">پذیرفته</span>' : r === 'DUPLICATE' ? '<span class="pill">تکراری</span>' : '<span class="pill bad">رد شد</span>';

  /* ---------- screens ---------- */
  const SCREENS = {};
  const ORDER = [];
  function def(id, meta) { SCREENS[id] = Object.assign({ id }, meta); ORDER.push(id); }

  def('welcome', {
    name: 'Welcome', fa: 'خوش‌آمد', stage: 'First run', preset: 'fresh', path: ['welcome'],
    render: (s, p) => `
      <div class="body center" style="gap:22px;padding-top:12px">
        <div class="hero-mark">${p.wf ? 'لوگو' : LOGO}</div>
        <div style="text-align:center;display:flex;flex-direction:column;gap:4px">
          <h2 class="t-display">آسان‌کارت</h2>
          <p class="t-muted">شنونده پیامک بانک برای فروشگاه شما</p>
        </div>
        <div class="points">
          ${[['shield', 'فقط مبلغ و زمان واریز ارسال می‌شود', 'مانده حساب و متن پیامک هرگز از این گوشی خارج نمی‌شود.'],
             ['code', 'متن‌باز و شفاف', 'کد برنامه عمومی است و هر کسی می‌تواند آن را بررسی کند.'],
             ['link', 'به هر سرویس سازگار وصل می‌شود', 'آسان‌کارت پیش‌فرض است. سرویس خودتان را هم می‌توانید اضافه کنید.']]
            .map(([i, t, d]) => `<div class="point"><span class="lead pri">${ic(i)}</span><div><p class="t-h2">${t}</p><p class="t-small t-muted">${d}</p></div></div>`).join('')}
        </div>
      </div>
      <div class="foot"><button class="btn block" data-act="start">شروع</button><p class="t-cap" style="text-align:center">نسخه ۱٫۰٫۰ · Apache-2.0</p></div>`,
  });

  def('profile', {
    name: 'Default profile', fa: 'انتخاب سرویس', stage: 'First run', preset: 'fresh', path: ['welcome', 'profile'],
    render: (s, p) => {
      const d = s.draft;
      const opt = (v, lead, title, tag, sub, extra) => `
        <div class="card tap ${d.service === v ? 'sel' : ''}" data-act="pickService" data-v="${v}" role="radio" aria-checked="${d.service === v}" tabindex="0">
          <div class="hd"><span class="radio ${d.service === v ? 'on' : ''}"></span>${lead}<div class="grow"><p class="t-h2">${title} ${tag}</p><p class="t-small t-muted">${sub}</p></div></div>
          ${d.service === v ? extra : ''}
        </div>`;
      return `${bar(d.isNew ? 'پروفایل جدید' : 'پروفایل پیش‌فرض', { back: true })}
      <div class="body">
        <p class="t-small t-muted">${d.isNew ? 'هر پروفایل یک اتصال جداست: یک سرویس، یک فروشگاه و کارت‌هایی که برایش گوش می‌دهید.' : 'اولین پروفایل همین حالا ساخته شد. فقط بگویید به کجا وصل شود.'}</p>
        ${field({ id: 'pf-name', label: 'نام پروفایل', bind: 'draft.name', value: d.name })}
        <p class="t-sec">به کجا وصل شود؟</p>
        ${opt('asancart', `<span class="lead pri" style="padding:6px">${p.wf ? ic('link') : LOGO}</span>`, 'آسان‌کارت', '<span class="pill ok nodot">پیشنهادی</span>',
          'سرویس رسمی. آدرس‌ها از قبل تنظیم شده‌اند.',
          `<dl class="kv"><dt>آدرس API</dt><dd>${ltr(host(ASANCART_URL) + '/v1/device', 'mono')}</dd><dt>قالب‌ها</dt><dd>از همین سرویس، خودکار</dd></dl>`)}
        ${opt('custom', `<span class="lead">${ic('code')}</span>`, 'سرویس دیگر', '',
          'هر سرویسی که Device API آسان‌کارت را پیاده کرده باشد.',
          field({ id: 'pf-url', label: 'آدرس پایه API', bind: 'draft.customUrl', value: d.customUrl, ph: 'https://example.com/v1/device', ltr: true, mono: true, err: s.urlErr ? 'آدرس را کامل وارد کنید؛ با https:// شروع می‌شود.' : '', help: 'بعد از این، کد QR همان سرویس را اسکن می‌کنید.' }))}
      </div>
      <div class="foot"><button class="btn block" data-act="toConnect">ادامه</button></div>`;
    },
  });

  def('connect', {
    name: 'Scan QR', fa: 'اسکن QR', stage: 'First run', preset: 'fresh', path: ['welcome', 'profile', 'connect'],
    render: (s, p) => {
      const d = s.draft;
      const svc = d.service === 'asancart' ? 'آسان‌کارت' : 'سرویس';
      return `${bar('اتصال به حساب')}
      <div class="body">
        <p class="t-small">در داشبورد ${svc}، بخش <b>دستگاه‌ها</b> را باز کنید و <b>افزودن دستگاه</b> را بزنید. بعد کد QR را اینجا اسکن کنید.</p>
        <div class="vf" data-act="scan" role="button" tabindex="0" aria-label="اسکن نمونه">
          <svg class="qrghost" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.4">${ICONS.qr}</svg>
          <div class="frame"><i></i><i></i><i></i><i></i></div>
          ${p.ui.busy === 'scan' ? '' : '<div class="scan"></div>'}
          <div class="hint">${p.ui.busy === 'scan' ? '<span class="spin" style="display:inline-block;vertical-align:middle"></span> در حال اتصال…' : 'کد QR را داخل کادر بگیرید'}</div>
        </div>
        ${field({ id: 'cn-dev', label: 'نام این گوشی', bind: 'draft.deviceName', value: d.deviceName, help: 'در داشبورد فروشگاه با همین نام دیده می‌شود.' })}
      </div>
      <div class="foot"><button class="btn tonal block" data-go="connect-manual">${ic('keyboard')} وارد کردن دستی کد</button></div>`;
    },
  });

  def('connect-manual', {
    name: 'Enter code by hand', fa: 'ورود دستی', stage: 'First run', preset: 'fresh', path: ['welcome', 'profile', 'connect', 'connect-manual'],
    render: (s, p) => {
      const d = s.draft;
      const url = d.service === 'asancart' ? ASANCART_URL : d.customUrl;
      return `${bar('وارد کردن دستی')}
      <div class="body">
        <p class="t-small t-muted">اگر دوربین کار نمی‌کند یا داشبورد را روی همین گوشی باز کرده‌اید، کد زیر QR را اینجا بنویسید.</p>
        ${field({ id: 'cm-url', label: 'آدرس سرویس', bind: 'draft.url', value: url, ltr: true, mono: true, ro: d.service === 'asancart', help: d.service === 'asancart' ? 'آدرس آسان‌کارت ثابت است.' : '' })}
        ${field({ id: 'cm-code', label: 'کد اتصال', bind: 'draft.code', value: d.code, ph: 'K7QM-2XPA', ltr: true, mono: true,
          help: 'یک‌بار مصرف است و ۱۰ دقیقه اعتبار دارد.',
          err: s.codeErr ? 'این کد نامعتبر است، قبلاً استفاده شده یا منقضی شده. در داشبورد کد تازه بسازید.' : '' })}
        ${field({ id: 'cm-dev', label: 'نام این گوشی', bind: 'draft.deviceName', value: d.deviceName })}
      </div>
      <div class="foot"><button class="btn block" data-act="pairManual" ${p.ui.busy ? 'disabled' : ''}>${p.ui.busy === 'pair' ? '<span class="spin"></span> در حال اتصال…' : 'اتصال'}</button></div>`;
    },
  });

  def('connected', {
    name: 'Connected', fa: 'وصل شد', stage: 'First run', preset: 'paired', path: ['connected'],
    render: (s) => {
      const pr = prof(s);
      return `<div class="body" style="padding-top:18px">
        <div class="okmark">${ic('check')}</div>
        <div style="text-align:center;display:flex;flex-direction:column;gap:2px"><h2 class="t-display">وصل شد</h2>
        <p class="t-muted t-small">این گوشی به فروشگاه «${pr.shop}» اضافه شد.</p></div>
        <div class="card"><dl class="kv">
          <dt>پروفایل</dt><dd>${esc(pr.name)}</dd>
          <dt>سرویس</dt><dd>${ltr(host(pr.url), 'mono')}</dd>
          <dt>نام گوشی</dt><dd>${ltr(pr.deviceName)}</dd>
        </dl></div>
        <p class="t-sec">کارت‌های تأییدشده این فروشگاه</p>
        <div class="list">${cardsOf(pr).map((c) => `<div class="li">${mini(c)}<div class="grow"><span class="ttl">${c.bankName}</span><span class="sub">${ltr(c.masked, 'mono')}</span></div></div>`).join('')}</div>
      </div>
      <div class="foot"><button class="btn block" data-act="toChecklist">ادامه راه‌اندازی</button></div>`;
    },
  });

  def('checklist', {
    name: 'Setup checklist', fa: 'راه‌اندازی', stage: 'Setup', preset: 'setup-mid', path: ['checklist'],
    render: (s) => {
      const pr = prof(s);
      const st = steps(s, pr);
      const cards = cardsOf(pr);
      const locked = !(st.one || listenersDone(pr)) || !(s.perms.receive && s.perms.read);
      const num = (done, n) => `<span class="stepn ${done ? 'done' : ''}">${done ? ic('check') : fd(n)}</span>`;
      return `${bar('راه‌اندازی «' + esc(pr.name) + '»', { back: false })}
      <div class="body">
        <div class="card flat">
          <div class="hd"><div class="grow"><p class="t-h2">${fd(st.count)} از ۳ مرحله انجام شد</p><p class="t-cap">هر مرحله که تمام شود تیک می‌خورد.</p></div></div>
          <div class="prog"><i style="width:${(st.count / 3) * 100}%"></i></div>
        </div>
        <div class="card">
          <div class="hd">${num(st.one, 1)}<div class="grow"><p class="t-h2">شنونده کارت‌ها</p><p class="t-small t-muted">${fn(listenersDone(pr))} از ${fn(cards.length)} کارت آماده است</p></div></div>
          <div class="sublist">${cards.map((c) => {
            const l = pr.listeners[c.id];
            return `<div class="subrow" data-act="openListener" data-v="${c.id}" role="button" tabindex="0">${mini(c)}<span class="grow">${c.short} ${fd(c.last4)}</span>${l ? '<span class="pill ok">آماده</span>' : '<span class="pill warn">تنظیم نشده</span>'}${ic('chev')}</div>`;
          }).join('')}</div>
        </div>
        <div class="card tap" data-go="permissions" role="button" tabindex="0">
          <div class="hd">${num(st.two, 2)}<div class="grow"><p class="t-h2">دسترسی‌ها</p><p class="t-small t-muted">${fn(permsDone(s))} از ${fn(REQUIRED_PERMS.length)} دسترسی لازم داده شده</p></div>${ic('chev')}</div>
        </div>
        <div class="card ${locked ? 'lock' : 'tap'}" ${locked ? '' : 'data-go="activate" role="button" tabindex="0"'}>
          <div class="hd">${num(st.three, 3)}<div class="grow"><p class="t-h2">روشن کردن و آزمایش</p><p class="t-small t-muted">${locked ? 'اول کارت‌ها و دسترسی پیامک را تنظیم کنید.' : st.three ? 'روشن است و سیگنال آزمایشی رسید.' : 'پروفایل را روشن کنید و یک سیگنال آزمایشی بفرستید.'}</p></div>${locked ? ic('lock') : ic('chev')}</div>
        </div>
      </div>
      <div class="foot">${st.count === 3 ? '<button class="btn block" data-act="finish">رفتن به خانه</button>' : '<button class="btn text block" data-act="later">بعداً ادامه می‌دهم</button>'}</div>`;
    },
  });

  def('listener', {
    name: 'Card listener', fa: 'شنونده کارت', stage: 'Setup', preset: 'paired', path: ['checklist', 'listener'],
    render: (s, p) => {
      const pr = prof(s);
      const c = CARDS[s.cur.card];
      const l = pr.listeners[c.id] || {};
      const pick = p.ui.listenerDraft || { sender: l.sender || SENDERS[c.bank][0].id, tpl: l.tpl || DEFAULT_TPL[c.bank] };
      p.ui.listenerDraft = pick;
      const tpl = tplById(s, pick.tpl);
      const last = LAST_SMS[c.bank];
      return `${bar('شنونده کارت')}
      <div class="body">
        <div class="bcard ${c.bank}"><div class="top"><span>${c.bankName}</span><span class="chip"></span></div><div class="num">${c.masked}</div></div>
        <p class="t-sec">پیامک‌های این کارت از کدام فرستنده می‌آید؟</p>
        <div class="banner info"><span>${ic('info')}</span><p class="t-small">بر اساس قالب رسمی ${c.bankName} و پیامک‌های همین گوشی پیشنهاد شد.</p></div>
        <div class="list">${SENDERS[c.bank].map((x) => `
          <div class="li tap" data-act="pickSender" data-v="${esc(x.id)}" role="radio" aria-checked="${pick.sender === x.id}" tabindex="0">
            <span class="radio ${pick.sender === x.id ? 'on' : ''}"></span>
            <div class="grow"><span class="ttl">${ltr(x.id, 'mono')} ${x.suggested ? '<span class="pill ok nodot">پیشنهادی</span>' : ''}</span><span class="sub">${fn(x.count)} پیامک در ۳۰ روز گذشته · ${x.note}</span></div>
          </div>`).join('')}
          <div class="li tap" data-act="toast" data-v="در نسخه واقعی، شماره یا نام فرستنده را اینجا می‌نویسید." role="button" tabindex="0"><span class="lead" style="width:20px;height:20px;background:none">${ic('plus')}</span><div class="grow"><span class="ttl">فرستنده دیگر…</span></div></div>
        </div>
        <p class="t-sec">قالب خواندن پیامک</p>
        <div class="list"><div class="li"><span class="lead">${ic('doc')}</span><div class="grow"><span class="ttl">${ltr(tpl.name, 'mono')}</span><span class="sub">${tpl.bankName} · نسخه ${fn(tpl.v)} · ${TEMPLATES.includes(tpl) ? 'رسمی، خودکار انتخاب شد' : 'سفارشی'}</span></div><button class="btn text sm" data-act="chooseTpl">تغییر</button></div></div>
        <p class="t-sec">آزمایش روی آخرین پیامک این فرستنده</p>
        <div class="card">
          <div class="sms">${esc(last.text)}</div>
          <p class="t-cap">${last.at} · فقط روی همین گوشی</p>
          <div class="hd"><span class="pill ok">واریز</span><span class="grow"></span><span class="amount">${rial(last.amount)}</span></div>
          <dl class="kv"><dt>ارسال می‌شود</dt><dd>مبلغ، زمان دریافت، کارت</dd><dt>ارسال نمی‌شود</dt><dd>مانده، شماره حساب، متن پیامک</dd></dl>
        </div>
      </div>
      <div class="foot"><button class="btn block" data-act="saveListener">ذخیره شنونده</button></div>`;
    },
  });

  const PERMS = [
    { k: 'receive', icon: 'sms', t: 'دریافت پیامک', need: true, why: 'پیامک تازه بانک را همان لحظه می‌گیرد، حتی وقتی برنامه بسته است.', btn: 'اجازه دادن' },
    { k: 'read', icon: 'inbox', t: 'خواندن صندوق پیامک', need: true, why: 'پیامک‌هایی را که وقتی گوشی خاموش بود یا برنامه متوقف شده بود رسیده‌اند، دوباره بررسی می‌کند.', btn: 'اجازه دادن' },
    { k: 'battery', icon: 'battery', t: 'بدون محدودیت باتری', need: true, why: 'نمی‌گذارد اندروید برای صرفه‌جویی، برنامه را متوقف کند.', btn: 'باز کردن تنظیمات' },
    { k: 'notif', icon: 'bell', t: 'اعلان‌ها', need: false, why: 'اگر ارسال گیر کند یا پیامکی شناخته نشود، خبرتان می‌کند.', btn: 'اجازه دادن' },
    { k: 'autostart', icon: 'power', t: 'اجرای خودکار', need: false, why: 'بعضی گوشی‌ها (شیائومی، هواوی، اوپو) این را جدا دارند. برنامه نمی‌تواند وضعیتش را بخواند؛ بعد از روشن کردن، تأیید کنید.', btn: 'باز کردن تنظیمات', manual: true },
  ];
  def('permissions', {
    name: 'Permissions', fa: 'دسترسی‌ها', stage: 'Setup', preset: 'setup-mid', path: ['checklist', 'permissions'],
    render: (s) => {
      const all = permsDone(s) === REQUIRED_PERMS.length;
      return `${bar('دسترسی‌ها')}
      <div class="body">
        <p class="t-small t-muted">برنامه برای کار در پس‌زمینه به این‌ها نیاز دارد. سه مورد اول لازم است.</p>
        ${PERMS.map((x) => {
          const on = s.perms[x.k];
          const pill = on ? '<span class="pill ok">داده شده</span>' : x.manual ? '<span class="pill warn">بررسی نشده</span>' : x.need ? '<span class="pill bad">لازم</span>' : '<span class="pill warn">توصیه‌شده</span>';
          const tone = on ? 'ok' : x.need ? 'bad' : 'warn';
          return `<div class="card"><div class="hd"><span class="lead ${tone}">${ic(x.icon)}</span><div class="grow"><p class="t-h2">${x.t}</p></div>${pill}</div>
            <p class="t-small t-muted">${x.why}</p>
            ${on ? '' : `<div class="row-btns"><button class="btn ${x.need ? '' : 'tonal'} sm" data-act="grant" data-v="${x.k}">${x.btn}</button></div>`}</div>`;
        }).join('')}
        <p class="t-cap">${ic('camera', '')} دسترسی دوربین فقط موقع اسکن کد QR پرسیده می‌شود.</p>
      </div>
      <div class="foot">${all ? '<button class="btn block" data-back>تمام</button>' : '<button class="btn block" data-act="grantAll">اجازه دادن همه</button>'}</div>`;
    },
  });

  function testBlock(pr, p) {
    const t = pr.test;
    if (p.ui.busy === 'test') return '<div class="banner info"><span class="spin"></span><p class="t-small">در حال ارسال سیگنال…</p></div>';
    if (!t) return '';
    if (t.state === 'ok') return `<div class="banner ok"><span>${ic('check')}</span><div><p class="t-h2">سیگنال رسید · ${t.at}</p><p class="t-small">«${esc(pr.deviceName)}» حالا در داشبورد «${pr.shop}» آنلاین دیده می‌شود. زمان پاسخ ${fn(t.ms)} میلی‌ثانیه.</p></div></div>`;
    return `<div class="banner bad"><span>${ic('offline')}</span><div><p class="t-h2">سیگنال نرسید</p><p class="t-small">${t.msg}</p></div></div>`;
  }

  def('activate', {
    name: 'Turn on and test', fa: 'روشن کردن و آزمایش', stage: 'Setup', preset: 'ready', path: ['checklist', 'activate'],
    galleryPreset: 'tested',
    render: (s, p) => {
      const pr = prof(s);
      const b = blockers(s, pr);
      const r = s.smsResult;
      return `${bar('روشن کردن و آزمایش')}
      <div class="body">
        <div class="card ${pr.enabled ? 'sel' : ''}">
          <div class="hd"><span class="lead ${pr.enabled ? 'ok' : ''}">${ic('power')}</span><div class="grow"><p class="t-h2">${esc(pr.name)}</p><p class="t-small t-muted">${pr.enabled ? 'روشن · پیامک‌ها را می‌شنود و می‌فرستد' : 'خاموش · پیامکی خوانده یا ارسال نمی‌شود'}</p></div>${sw(pr.enabled, 'toggleProfile', pr.id, b.length)}</div>
          ${b.map((x) => `<div class="subrow" data-go="${x.go}" role="button" tabindex="0"><span style="color:var(--ac-warn)">${ic('warn')}</span><span class="grow">${x.t}</span>${ic('chev')}</div>`).join('')}
        </div>
        <p class="t-sec">آزمایش اتصال</p>
        <div class="card">
          <p class="t-small">یک سیگنال به ${ltr(host(pr.url), 'mono')} می‌فرستیم. اگر برسد، این گوشی در داشبورد فروشگاه آنلاین دیده می‌شود.</p>
          ${testBlock(pr, p)}
          <button class="btn outline block" data-act="runTest" ${p.ui.busy ? 'disabled' : ''}>${ic('signal')} ارسال سیگنال آزمایشی</button>
        </div>
        <p class="t-sec">آزمایش یک پیامک</p>
        <div class="card">
          <p class="t-small t-muted">متن یک پیامک بانک را اینجا بچسبانید. فقط روی همین گوشی بررسی می‌شود و چیزی ارسال نمی‌شود.</p>
          ${field({ id: 'ac-sms', label: 'متن پیامک', bind: 'smsSample', value: s.smsSample, area: true })}
          <button class="btn tonal block" data-act="runSmsTest">${ic('flask')} بررسی پیامک</button>
          ${r ? (r.status === 'DEPOSIT'
            ? `<div class="hd"><span class="pill ok">واریز</span><span class="grow"></span><span class="amount">${rial(r.amount)}</span></div>
               <dl class="kv"><dt>قالب</dt><dd>${ltr(r.tpl, 'mono')} · نسخه ${fn(r.v)}</dd><dt>کارت</dt><dd>${r.card ? CARDS[r.card].short + ' ' + fd(CARDS[r.card].last4) : 'شنونده‌ای برای این فرستنده نیست'}</dd><dt>اگر واقعی بود</dt><dd>به ${esc(host(pr.url))} فرستاده می‌شد</dd></dl>`
            : `<div class="hd"><span class="pill warn">${STATUS_FA[r.status]}</span></div><p class="t-small t-muted">${r.status === 'IGNORED' ? 'این پیامک برداشت یا پیام دیگر بانک است و ارسال نمی‌شود.' : 'در کار واقعی، این پیامک به فهرست «شناخته‌نشده» می‌رفت تا بررسی کنید.'}</p>`) : ''}
        </div>
      </div>
      <div class="foot"><button class="btn block" data-act="finish">${s.onboarded ? 'ذخیره' : 'تمام'}</button></div>`;
    },
  });

  def('home', {
    name: 'Home', fa: 'خانه', stage: 'Daily use', preset: 'daily', path: ['home'], tab: true,
    render: (s, p) => {
      const broken = s.profiles.filter((x) => x.enabled && x.lastError);
      return `<header class="abar big"><div class="brand">${p.wf ? '<span class="lead" style="width:34px;height:34px">' + ic('card') + '</span>' : LOGO}<b>آسان‌کارت</b></div></header>
      <div class="body" style="padding-bottom:90px">
        ${broken.length
          ? `<div class="banner bad tap" data-act="openProfile" data-v="${broken[0].id}" role="button" tabindex="0"><span>${ic('warn')}</span><div class="grow"><p class="t-h2">«${esc(broken[0].name)}» نمی‌تواند ارسال کند</p><p class="t-small">${fn(broken[0].queue)} واریز در صف مانده است.</p></div></div>`
          : '<div class="banner ok"><span>' + ic('check') + '</span><p class="t-h2">همه پروفایل‌های روشن کار می‌کنند</p></div>'}
        ${s.unparsed.length ? `<div class="banner tap" data-act="openUnparsed" role="button" tabindex="0"><span>${ic('sms')}</span><div class="grow"><p class="t-h2">${fn(s.unparsed.length)} پیامک شناخته نشد</p><p class="t-small">بررسی کنید تا واریزی از قلم نیفتد.</p></div>${ic('chev')}</div>` : ''}
        <p class="t-sec">پروفایل‌ها</p>
        ${s.profiles.map((pr) => {
          const setupDone = steps(s, pr).one && pr.connected;
          return `<div class="card tap" data-act="openProfile" data-v="${pr.id}" role="button" tabindex="0">
            <div class="hd"><span class="lead ${pr.service === 'asancart' ? 'pri' : ''}" style="${pr.service === 'asancart' && !p.wf ? 'padding:6px' : ''}">${pr.service === 'asancart' && !p.wf ? LOGO : ic(pr.service === 'asancart' ? 'link' : 'code')}</span>
              <div class="grow"><p class="t-h2">${esc(pr.name)}</p><p class="t-cap">${esc(pr.shop || 'وصل نشده')} · ${ltr(host(pr.url), 'mono')}</p></div>
              ${sw(pr.enabled, 'toggleProfile', pr.id, blockers(s, pr).length)}</div>
            <div class="stat"><div><b>${fn(pr.queue)}</b><span>در صف</span></div><div><b>${pr.lastSms || '—'}</b><span>از آخرین پیامک</span></div><div><b>${pr.lastSignal || '—'}</b><span>از آخرین سیگنال</span></div></div>
            <div class="chips">${cardsOf(pr).filter((c) => pr.listeners[c.id]).map((c) => `<span class="tchip">${mini(c)}${c.short} ${fd(c.last4)}</span>`).join('')}${!pr.enabled ? '<span class="pill">خاموش</span>' : ''}</div>
            ${pr.enabled && pr.lastError ? `<p class="t-small" style="color:var(--ac-bad)">${pr.lastError}</p>` : ''}
            ${!setupDone ? '<div class="hd"><span class="pill warn">راه‌اندازی ناتمام</span><span class="grow"></span><button class="btn text sm" data-act="resumeSetup" data-v="' + pr.id + '">ادامه</button></div>' : ''}
          </div>`;
        }).join('')}
      </div>
      <button class="fab" data-act="newProfile">${ic('plus')} پروفایل جدید</button>
      ${navBar('home', s)}`;
    },
  });

  def('profile-detail', {
    name: 'Profile', fa: 'پروفایل', stage: 'Daily use', preset: 'daily', path: ['home', 'profile-detail'],
    render: (s, p) => {
      const pr = prof(s);
      const hb = pr.heartbeat;
      const t = pr.test;
      return `${bar(esc(pr.name))}
      <div class="body">
        <div class="card ${pr.enabled ? 'sel' : ''}"><div class="hd"><span class="lead ${pr.enabled ? 'ok' : ''}">${ic('power')}</span><div class="grow"><p class="t-h2">${pr.enabled ? 'روشن' : 'خاموش'}</p><p class="t-small t-muted">${pr.enabled ? 'پیامک‌ها را می‌شنود و می‌فرستد.' : 'پیامکی خوانده یا ارسال نمی‌شود.'}</p></div>${sw(pr.enabled, 'toggleProfile', pr.id, blockers(s, pr).length)}</div></div>
        <p class="t-sec">اتصال</p>
        <div class="card">
          <dl class="kv"><dt>سرویس</dt><dd>${ltr(host(pr.url), 'mono')}</dd><dt>فروشگاه</dt><dd>${esc(pr.shop)}</dd><dt>نام گوشی</dt><dd>${ltr(pr.deviceName)}</dd></dl>
          ${testBlock(pr, p)}
          <button class="btn outline block" data-act="runTest" ${p.ui.busy ? 'disabled' : ''}>${ic('signal')} ارسال سیگنال آزمایشی</button>
        </div>
        <p class="t-sec">شنونده کارت‌ها</p>
        <div class="list">${cardsOf(pr).map((c) => {
          const l = pr.listeners[c.id];
          const tp = l && tplById(s, l.tpl);
          return `<div class="li tap" data-act="openListener" data-v="${c.id}" role="button" tabindex="0">${mini(c)}<div class="grow"><span class="ttl">${c.short} ${fd(c.last4)}</span><span class="sub">${l ? `فرستنده ${ltr(l.sender, 'mono')} · ${ltr(tp.name, 'mono')} نسخه ${fn(tp.v)}` : 'تنظیم نشده'}</span></div>${l ? '' : '<span class="pill warn">تنظیم نشده</span>'}${ic('chev')}</div>`;
        }).join('')}</div>
        <p class="t-sec">صف ارسال</p>
        <div class="card">
          <div class="stat"><div><b>${fn(pr.queue)}</b><span>در صف</span></div><div><b>${fn(pr.sentToday)}</b><span>ارسال امروز</span></div><div><b>${pr.lastSend || '—'}</b><span>آخرین ارسال</span></div></div>
          ${pr.lastError ? `<div class="banner bad"><span>${ic('offline')}</span><p class="t-small">${pr.lastError}</p></div>` : ''}
          ${pr.queue ? '<button class="btn tonal sm" data-act="flush" data-v="' + pr.id + '">' + ic('send') + ' ارسال همین الان</button>' : ''}
        </div>
        <p class="t-sec">سیگنال وضعیت</p>
        <div class="card">
          <div class="hd"><div class="grow"><p class="t-h2">سیگنال دوره‌ای</p><p class="t-small t-muted">${hb.on ? 'اگر ۳ سیگنال پشت سر هم نرسد، داشبورد به شما هشدار می‌دهد.' : 'خاموش است؛ داشبورد از قطع شدن این گوشی باخبر نمی‌شود.'}</p></div>${sw(hb.on, 'toggleHb', pr.id)}</div>
          ${hb.on ? `<div class="seg" role="radiogroup">${[[60, '۱ دقیقه'], [300, '۵ دقیقه'], [900, '۱۵ دقیقه'], [3600, '۱ ساعت']].map(([v, l]) => `<button class="${hb.interval === v ? 'on' : ''}" data-act="setHb" data-v="${v}">${l}</button>`).join('')}</div>` : ''}
        </div>
        <button class="btn danger block" data-act="askRemove">${ic('trash')} قطع اتصال و حذف پروفایل</button>
      </div>`;
    },
  });

  def('activity', {
    name: 'Activity', fa: 'فعالیت', stage: 'Daily use', preset: 'daily', path: ['activity'], tab: true,
    render: (s) => {
      const tab = s.activityTab;
      const pn = (id) => (s.profiles.find((x) => x.id === id) || {}).name || '';
      let list = '';
      if (tab === 'sent') {
        list = s.reports.length ? `<p class="t-sec">امروز · ۲۰ آبان</p><div class="list">${s.reports.map((r) => {
          const c = CARDS[r.card];
          return `<div class="li"><span class="lead ${r.result === 'ACCEPTED' ? 'ok' : r.result === 'REJECTED' ? 'bad' : ''}">${ic(r.result === 'REJECTED' ? 'x' : 'check')}</span><div class="grow"><span class="ttl amount" style="font-size:17px">${rial(r.amount)}</span><span class="sub">${c.short} ${fd(c.last4)} · ${r.at} · ${esc(pn(r.profile))}</span>${r.reason ? `<span class="sub" style="color:var(--ac-bad)">${r.reason}</span>` : ''}</div>${resultPill(r.result)}</div>`;
        }).join('')}</div>` : '<p class="t-muted t-small" style="text-align:center;padding:40px 0">هنوز واریزی ارسال نشده است.</p>';
      } else if (tab === 'queue') {
        const bad = s.profiles.find((x) => x.queue && x.lastError);
        list = (bad ? `<div class="banner bad"><span>${ic('offline')}</span><div><p class="t-h2">${esc(bad.name)}</p><p class="t-small">${bad.lastError}</p></div></div>` : '')
          + (s.queue.length ? `<div class="list">${s.queue.map((r) => {
            const c = CARDS[r.card];
            return `<div class="li"><span class="lead warn">${ic('clock')}</span><div class="grow"><span class="ttl amount" style="font-size:17px">${rial(r.amount)}</span><span class="sub">${c.short} ${fd(c.last4)} · ${r.at} · ${esc(pn(r.profile))}</span></div><span class="pill warn">در انتظار</span></div>`;
          }).join('')}</div><button class="btn tonal" data-act="flush" data-v="p2">${ic('send')} ارسال همین الان</button>` : '<p class="t-muted t-small" style="text-align:center;padding:40px 0">صف خالی است.</p>');
      } else {
        list = `<p class="t-small t-muted">این پیامک‌ها از فرستنده یکی از کارت‌ها آمده‌اند ولی هیچ قالبی آن‌ها را نخواند. فقط روی همین گوشی‌اند.</p>`
          + (s.unparsed.length ? `<div class="list">${s.unparsed.map((u) => `<div class="li tap" data-act="openSms" data-v="${u.id}" role="button" tabindex="0"><span class="lead warn">${ic('sms')}</span><div class="grow"><span class="ttl" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(u.text.split('\n')[1] || u.text)}</span><span class="sub">${ltr(u.sender, 'mono')} · ${u.at} · ${STATUS_FA[u.status]}</span></div>${ic('chev')}</div>`).join('')}</div>` : '<p class="t-muted t-small" style="text-align:center;padding:40px 0">همه پیامک‌ها خوانده شدند.</p>');
      }
      return `${bigBar('فعالیت')}
      <div class="body">
        <div class="tabs" role="tablist">
          <button class="${tab === 'sent' ? 'on' : ''}" data-act="actTab" data-v="sent">ارسال‌شده</button>
          <button class="${tab === 'queue' ? 'on' : ''}" data-act="actTab" data-v="queue">در صف ${s.queue.length ? `<span class="badge" style="background:var(--ac-warn)">${fn(s.queue.length)}</span>` : ''}</button>
          <button class="${tab === 'unparsed' ? 'on' : ''}" data-act="actTab" data-v="unparsed">شناخته‌نشده ${s.unparsed.length ? `<span class="badge">${fn(s.unparsed.length)}</span>` : ''}</button>
        </div>
        ${list}
      </div>
      ${navBar('activity', s)}`;
    },
  });

  def('sms-detail', {
    name: 'Unparsed SMS', fa: 'پیامک شناخته‌نشده', stage: 'Daily use', preset: 'daily', path: ['activity', 'sms-detail'],
    render: (s) => {
      const u = s.unparsed.find((x) => x.id === s.cur.sms) || UNPARSED[0];
      const c = CARDS[u.card];
      return `${bar('پیامک شناخته‌نشده')}
      <div class="body">
        <div class="card">
          <div class="hd"><span class="lead warn">${ic('sms')}</span><div class="grow"><p class="t-h2">${ltr(u.sender, 'mono')}</p><p class="t-cap">${u.at} · کارت ${c.short} ${fd(c.last4)}</p></div></div>
          <div class="sms">${esc(u.text)}</div>
        </div>
        <div class="banner info"><span>${ic('eyeoff')}</span><p class="t-small">این متن فقط روی همین گوشی است و به هیچ سرویسی فرستاده نشده.</p></div>
        <p class="t-sec">چرا خوانده نشد؟</p>
        <div class="card"><p class="t-small">${u.reason}</p><dl class="kv"><dt>قالب‌های بررسی‌شده</dt><dd>${ltr(u.tried, 'mono')}</dd><dt>نتیجه</dt><dd>${STATUS_FA[u.status]}</dd></dl></div>
        <p class="t-sec">چه کار کنم؟</p>
        <div class="list">
          <div class="li tap" data-act="retrySms" role="button" tabindex="0"><span class="lead">${ic('refresh')}</span><div class="grow"><span class="ttl">قالب‌ها را به‌روز کن و دوباره بخوان</span><span class="sub">اگر قالب تازه‌ای منتشر شده باشد، واریز ارسال می‌شود.</span></div></div>
          <div class="li tap" data-act="tplFromSms" role="button" tabindex="0"><span class="lead">${ic('edit')}</span><div class="grow"><span class="ttl">ساخت قالب سفارشی از این پیامک</span><span class="sub">فقط روی همین گوشی.</span></div></div>
          <div class="li tap" data-act="dismissSms" role="button" tabindex="0"><span class="lead bad">${ic('trash')}</span><div class="grow"><span class="ttl">واریز نیست، حذفش کن</span></div></div>
        </div>
      </div>`;
    },
  });

  def('templates', {
    name: 'Templates', fa: 'قالب‌ها', stage: 'Daily use', preset: 'daily', path: ['templates'], tab: true,
    render: (s) => {
      const row = (t, custom) => `<div class="li ${custom ? 'tap' : ''}" ${custom ? 'data-go="template-edit" role="button" tabindex="0"' : ''}><span class="lead">${ic('doc')}</span><div class="grow"><span class="ttl">${ltr(t.name, 'mono')}</span><span class="sub">${t.bankName} · نسخه ${fn(t.v)}</span></div>${t.dir === 'in' ? '<span class="pill info nodot">واریز</span>' : '<span class="pill nodot">نادیده‌گیری</span>'}${custom ? ic('chev') : ''}</div>`;
      return `${bigBar('قالب‌ها', `<button class="ibtn" data-act="refreshTpl" aria-label="به‌روزرسانی">${ic('refresh')}</button>`)}
      <div class="body">
        <div class="card flat"><div class="hd"><span class="lead ok">${ic('check')}</span><div class="grow"><p class="t-h2">قالب‌های رسمی به‌روز است</p><p class="t-cap">آخرین بررسی امروز ۱۴:۰۵ از ${ltr(host(ASANCART_URL), 'mono')}</p></div></div></div>
        <p class="t-sec">رسمی</p>
        <div class="list">${TEMPLATES.map((t) => row(t, false)).join('')}</div>
        <p class="t-sec">سفارشی · فقط روی این گوشی</p>
        ${s.custom.length ? `<div class="list">${s.custom.map((t) => row(t, true)).join('')}</div>` : ''}
        <button class="btn outline" data-act="newTpl">${ic('plus')} قالب سفارشی تازه</button>
        <div style="height:6px"></div>
      </div>
      ${navBar('templates', s)}`;
    },
  });

  def('template-edit', {
    name: 'Custom template', fa: 'قالب سفارشی', stage: 'Daily use', preset: 'daily', path: ['templates', 'template-edit'],
    render: (s) => {
      const t = s.tpl;
      const r = t.result;
      return `${bar('قالب سفارشی')}
      <div class="body">
        <p class="t-small t-muted">قالب سفارشی فقط روی همین گوشی است و همان شکل قالب‌های رسمی را دارد.</p>
        ${field({ id: 'te-name', label: 'نام', bind: 'tpl.name', value: t.name, ltr: true, mono: true, help: 'حروف کوچک انگلیسی، عدد و خط تیره.' })}
        ${field({ id: 'te-snd', label: 'فرستنده‌ها', bind: 'tpl.senders', value: t.senders, ltr: true, mono: true, help: 'با ویرگول جدا کنید.' })}
        ${field({ id: 'te-pat', label: 'الگو (regex)', bind: 'tpl.pattern', value: t.pattern, ltr: true, mono: true, area: true })}
        <p class="t-sec">تشخیص واریز</p>
        <div class="seg">${[['fixed', 'همیشه واریز'], ['sign', 'علامت + / −'], ['keyword', 'کلیدواژه']].map(([v, l]) => `<button class="${t.mode === v ? 'on' : ''}" data-act="tplMode" data-v="${v}">${l}</button>`).join('')}</div>
        ${t.mode === 'keyword' ? field({ id: 'te-kw', label: 'کلیدواژه‌های واریز', bind: 'tpl.kw', value: t.kw }) : ''}
        <div class="hd" style="display:flex;gap:10px"><div style="flex:1">${field({ id: 'te-pri', label: 'اولویت', bind: 'tpl.priority', value: t.priority, ltr: true, help: 'کمتر، زودتر.' })}</div>
          <div style="flex:1" class="fld"><label>واحد مبلغ</label><div class="seg">${[['rial', 'ریال'], ['toman', 'تومان']].map(([v, l]) => `<button class="${t.unit === v ? 'on' : ''}" data-act="tplUnit" data-v="${v}">${l}</button>`).join('')}</div></div></div>
        <p class="t-sec">آزمایش</p>
        ${field({ id: 'te-sample', label: 'نمونه پیامک', bind: 'tpl.sample', value: t.sample, area: true })}
        <button class="btn tonal" data-act="tplTest">${ic('flask')} آزمایش قالب</button>
        ${r ? (r.status === 'DEPOSIT'
          ? `<div class="card"><div class="hd"><span class="pill ok">واریز</span><span class="grow"></span><span class="amount">${rial(t.unit === 'toman' ? r.amount * 10 : r.amount)}</span></div><dl class="kv"><dt>amount</dt><dd>${ltr(fn(r.amount))}</dd><dt>reference</dt><dd>—</dd></dl></div>`
          : `<div class="card"><span class="pill warn">${STATUS_FA[r.status]}</span></div>`) : ''}
      </div>
      <div class="foot"><button class="btn block" data-act="saveTpl">ذخیره قالب</button></div>`;
    },
  });

  def('app-settings', {
    name: 'Settings', fa: 'تنظیمات', stage: 'Daily use', preset: 'daily', path: ['app-settings'], tab: true,
    render: (s, p) => {
      const th = p.o.theme;
      const missing = REQUIRED_PERMS.length - permsDone(s);
      return `${bigBar('تنظیمات')}
      <div class="body">
        <p class="t-sec">ظاهر</p>
        <div class="seg">${[['light', 'sun', 'روشن'], ['dark', 'moon', 'تیره'], ['system', 'auto', 'سیستم']].map(([v, i, l]) => `<button class="${th === v ? 'on' : ''}" data-act="setTheme" data-v="${v}">${ic(i)} ${l}</button>`).join('')}</div>
        <p class="t-sec">برنامه</p>
        <div class="list">
          <div class="li tap" data-go="permissions" role="button" tabindex="0"><span class="lead ${missing ? 'bad' : 'ok'}">${ic('shield')}</span><div class="grow"><span class="ttl">دسترسی‌ها</span><span class="sub">${missing ? fn(missing) + ' دسترسی لازم داده نشده' : 'همه دسترسی‌های لازم داده شده'}</span></div>${ic('chev')}</div>
          <div class="li"><span class="lead">${ic('refresh')}</span><div class="grow"><span class="ttl">به‌روزرسانی خودکار قالب‌ها</span><span class="sub">هر بار که برنامه باز شود و روزی یک بار</span></div>${sw(s.autoTemplates, 'toggleAutoTpl')}</div>
        </div>
        <p class="t-sec">حریم خصوصی</p>
        <div class="card">
          <div class="hd"><span class="lead ok">${ic('send')}</span><div class="grow"><p class="t-h2">ارسال می‌شود</p><p class="t-small t-muted">مبلغ واریز، زمان دریافت پیامک، کارت، و کد پیگیری اگر بانک لازم بداند.</p></div></div>
          <div class="hd"><span class="lead bad">${ic('eyeoff')}</span><div class="grow"><p class="t-h2">هرگز ارسال نمی‌شود</p><p class="t-small t-muted">مانده حساب، شماره حساب، متن پیامک و پیامک‌های دیگر.</p></div></div>
        </div>
        <p class="t-sec">درباره</p>
        <div class="list">
          <div class="li"><div class="grow"><span class="ttl">نسخه</span></div><span class="t-muted">۱٫۰٫۰</span></div>
          <div class="li"><div class="grow"><span class="ttl">کد منبع</span></div>${ltr('github.com/asancart/android', 'mono t-muted')}</div>
          <div class="li"><div class="grow"><span class="ttl">مجوز</span></div>${ltr('Apache-2.0', 't-muted')}</div>
        </div>
      </div>
      ${navBar('app-settings', s)}`;
    },
  });

  /* ---------- overlays ---------- */
  function sheetHtml(p) {
    const s = p.s;
    const sh = p.ui.sheet;
    if (!sh) return '';
    let inner = '';
    if (sh === 'tpl') {
      const c = CARDS[s.cur.card];
      const opts = TEMPLATES.filter((t) => t.bank === c.bank && t.dir === 'in').concat(s.custom);
      inner = `<p class="t-h2">قالب برای ${c.bankName}</p>
        <div class="list">${opts.map((t) => `<div class="li tap" data-act="pickTpl" data-v="${t.id}" role="radio" tabindex="0"><span class="radio ${p.ui.listenerDraft.tpl === t.id ? 'on' : ''}"></span><div class="grow"><span class="ttl">${ltr(t.name, 'mono')}</span><span class="sub">${TEMPLATES.includes(t) ? 'رسمی' : 'سفارشی'} · نسخه ${fn(t.v)}</span></div></div>`).join('')}</div>
        <button class="btn block" data-act="closeSheet">انتخاب</button>`;
    } else if (sh === 'autostart') {
      inner = `<p class="t-h2">روشن کردن اجرای خودکار</p>
        <p class="t-small t-muted">تنظیمات گوشی باز می‌شود. در شیائومی: برنامه‌ها ← مدیریت برنامه‌ها ← آسان‌کارت ← اجرای خودکار را روشن کنید. بعد برگردید و تأیید کنید.</p>
        <button class="btn block" data-act="confirmAutostart">روشن کردم</button><button class="btn text block" data-act="closeSheet">بعداً</button>`;
    } else if (sh === 'remove') {
      const pr = prof(s);
      inner = `<p class="t-h2">«${esc(pr.name)}» حذف شود؟</p>
        <p class="t-small t-muted">${pr.queue ? fn(pr.queue) + ' واریز در صف هرگز ارسال نمی‌شود. ' : ''}این گوشی تا وقتی در داشبورد لغو نشود، آنجا در فهرست دستگاه‌ها می‌ماند.</p>
        <button class="btn block" style="background:var(--ac-bad);color:#fff" data-act="removeProfile">حذف پروفایل</button><button class="btn text block" data-act="closeSheet">انصراف</button>`;
    }
    return `<div class="scrim" data-act="closeSheet"></div><div class="sheet" role="dialog"><div class="handle"></div>${inner}</div>`;
  }

  /* ---------- controller ---------- */
  const STATUS_BAR = (wf) => `<div class="sbar" style="direction:rtl"><span>۱۴:۳۲</span><span class="cam"></span><span class="sys">
      <svg viewBox="0 0 24 24"><path d="M2 20h3v-4H2zm5 0h3v-8H7zm5 0h3V8h-3zm5 0h3V4h-3z"/></svg>
      <svg viewBox="0 0 24 24"><path d="M3 9.5a13 13 0 0118 0l-1.6 1.7a10.6 10.6 0 00-14.8 0zM6.4 13a8.3 8.3 0 0111.2 0L16 14.7a6 6 0 00-8 0zM12 20l2.4-2.5a3.4 3.4 0 00-4.8 0z"/></svg>
      <svg viewBox="0 0 24 24"><rect x="2" y="7" width="18" height="10" rx="2" fill="none" stroke="currentColor" stroke-width="1.6"/><rect x="4" y="9" width="11" height="6" rx="1"/><rect x="21" y="10" width="1.6" height="4" rx=".8"/></svg></span></div>`;

  class Proto {
    constructor(o) {
      this.o = Object.assign({ skin: 'brand', theme: 'light', preset: 'fresh', screen: 'welcome', interactive: true }, o || {});
      this.wf = this.o.skin === 'wireframe';
      this.flags = { offline: false };
      this.ui = { busy: null, sheet: null, toast: null, listenerDraft: null };
      this.s = seed(this.o.preset);
      this.stack = (SCREENS[this.o.screen] && SCREENS[this.o.screen].path.slice()) || ['welcome'];
      this._timers = [];
    }
    get cur() { return this.stack[this.stack.length - 1]; }
    themeAttr() {
      const t = this.o.theme;
      if (t !== 'system') return t;
      const root = document.documentElement.getAttribute('data-theme');
      if (root === 'dark' || root === 'light') return root;
      return global.matchMedia && global.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    html() {
      const sc = SCREENS[this.cur];
      return `<div class="ac-phone ${this.wf ? 'wf' : ''} ${this.o.interactive ? '' : 'static'}" data-t="${this.themeAttr()}">
        <div class="scr" lang="fa" dir="rtl">${STATUS_BAR(this.wf)}${sc.render(this.s, this)}
        ${this.ui.toast ? `<div class="toast ${sc.tab ? '' : 'low'}" role="status">${this.ui.toast}</div>` : ''}
        ${sheetHtml(this)}
        <div class="gbar"><i></i></div></div></div>`;
    }
    mount(el) {
      this.el = el;
      el.addEventListener('click', (e) => this.onClick(e));
      el.addEventListener('keydown', (e) => {
        if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[role="button"],[role="radio"]')) { e.preventDefault(); e.target.click(); }
      });
      el.addEventListener('input', (e) => {
        const b = e.target.getAttribute('data-bind');
        if (!b) return;
        const parts = b.split('.');
        let o = this.s;
        while (parts.length > 1) o = o[parts.shift()];
        o[parts[0]] = e.target.value;
      });
      this.render();
    }
    render(keepScroll = true) {
      if (!this.el) return;
      const body = this.el.querySelector('.body');
      const top = keepScroll && body && this._lastScreen === this.cur ? body.scrollTop : 0;
      this.el.innerHTML = this.html();
      const nb = this.el.querySelector('.body');
      if (nb) nb.scrollTop = top;
      if (this._lastScreen !== this.cur && this.o.onChange) this.o.onChange(this.cur);
      this._lastScreen = this.cur;
    }
    later(ms, fn) { const t = setTimeout(fn, ms); this._timers.push(t); }
    clearTimers() { this._timers.forEach(clearTimeout); this._timers = []; }
    toast(msg) {
      this.ui.toast = msg; this.render();
      clearTimeout(this._toastT);
      this._toastT = setTimeout(() => { this.ui.toast = null; this.render(); }, 2600);
    }
    go(id, o = {}) {
      this.ui.sheet = null; this.ui.listenerDraft = null;
      if (o.replace) this.stack[this.stack.length - 1] = id; else this.stack.push(id);
      this.render(false);
    }
    back() {
      this.ui.sheet = null; this.ui.listenerDraft = null;
      if (this.stack.length > 1) this.stack.pop();
      this.render(false);
    }
    tab(id) { this.ui.sheet = null; this.stack = [id]; this.render(false); }
    jump(id) {
      const sc = SCREENS[id];
      if (!sc) return;
      this.clearTimers();
      this.ui = { busy: null, sheet: null, toast: null, listenerDraft: null };
      this.s = seed(sc.preset);
      if (id === 'activity') this.s.activityTab = 'sent';
      if (id === 'listener') this.s.cur.card = 'c2';
      if (id === 'profile-detail') this.s.cur.profile = 'p1';
      this.stack = sc.path.slice();
      this.render(false);
    }
    reset() { this.jump('welcome'); }
    setTheme(t) { this.o.theme = t; this.render(); }
    setFlag(k, v) { this.flags[k] = v; }

    onClick(e) {
      const t = e.target.closest('[data-act],[data-go],[data-back],[data-tab]');
      if (!t || !this.el.contains(t)) return;
      if (t.hasAttribute('data-back')) return this.back();
      if (t.hasAttribute('data-tab')) return this.tab(t.getAttribute('data-tab'));
      if (t.hasAttribute('data-go')) return this.go(t.getAttribute('data-go'));
      e.stopPropagation();
      const a = t.getAttribute('data-act');
      const v = t.getAttribute('data-v');
      const fnc = this.actions[a];
      if (fnc) fnc.call(this, v, t);
    }
  }

  Proto.prototype.actions = {
    start() { this.s.draft.isNew = false; this.go('profile'); },
    pickService(v) { this.s.draft.service = v; this.s.urlErr = false; this.render(); },
    toConnect() {
      const d = this.s.draft;
      if (d.service === 'custom' && !/^https:\/\/\S+/.test(d.customUrl || '')) { this.s.urlErr = true; return this.render(); }
      this.s.urlErr = false;
      this.go('connect');
    },
    scan() {
      if (this.ui.busy) return;
      this.ui.busy = 'scan'; this.render();
      this.later(1100, () => { this.ui.busy = null; this.actions.paired.call(this); });
    },
    pairManual() {
      const code = (this.s.draft.code || '').trim();
      if (!code || code === '0000') { this.s.codeErr = true; return this.render(); }
      this.s.codeErr = false; this.ui.busy = 'pair'; this.render();
      this.later(900, () => { this.ui.busy = null; this.actions.paired.call(this); });
    },
    paired() {
      const d = this.s.draft;
      let pr = this.s.profiles.find((x) => x.id === d.profileId);
      if (!pr) { pr = newProfile(d.profileId); this.s.profiles.push(pr); }
      Object.assign(pr, {
        name: d.name || 'پروفایل', service: d.service, url: d.service === 'asancart' ? ASANCART_URL : d.customUrl,
        deviceName: d.deviceName || 'Galaxy A54', connected: true, shop: d.isNew ? 'فروشگاه دوم نارنج' : SHOP.name, cards: d.isNew ? ['c2'] : ['c1', 'c2'],
      });
      this.s.cur.profile = pr.id;
      this.stack = ['connected'];
      this.render(false);
    },
    toChecklist() { this.stack = ['checklist']; this.render(false); },
    openListener(v) { this.s.cur.card = v; this.go('listener'); },
    pickSender(v) { this.ui.listenerDraft.sender = v; this.render(); },
    chooseTpl() { this.ui.sheet = 'tpl'; this.render(); },
    pickTpl(v) { this.ui.listenerDraft.tpl = v; this.render(); },
    closeSheet() { this.ui.sheet = null; this.render(); },
    saveListener() {
      const pr = prof(this.s);
      const c = CARDS[this.s.cur.card];
      pr.listeners[c.id] = Object.assign({ ok: true }, this.ui.listenerDraft);
      this.back();
      this.toast(`شنونده کارت ${c.short} ${fd(c.last4)} ذخیره شد`);
    },
    grant(k) {
      if (k === 'autostart') { this.ui.sheet = 'autostart'; return this.render(); }
      this.s.perms[k] = true;
      this.toast(k === 'battery' ? 'محدودیت باتری برداشته شد' : 'اجازه داده شد');
    },
    grantAll() { REQUIRED_PERMS.concat('notif').forEach((k) => { this.s.perms[k] = true; }); this.toast('دسترسی‌های لازم داده شد'); },
    confirmAutostart() { this.s.perms.autostart = true; this.ui.sheet = null; this.toast('اجرای خودکار تأیید شد'); },
    toggleProfile(v) {
      const pr = prof(this.s, v);
      const b = blockers(this.s, pr);
      if (b.length && !pr.enabled) return this.toast(b[0].t);
      pr.enabled = !pr.enabled;
      this.toast(pr.enabled ? `«${pr.name}» روشن شد` : `«${pr.name}» خاموش شد`);
    },
    runTest() {
      const pr = prof(this.s);
      this.ui.busy = 'test'; this.render();
      this.later(1000, () => {
        this.ui.busy = null;
        pr.test = this.flags.offline
          ? { state: 'fail', msg: 'سرویس جواب نداد و زمان انتظار تمام شد. اینترنت گوشی را بررسی کنید و دوباره بزنید.' }
          : { state: 'ok', at: '۱۴:۳۲', ms: 240 };
        if (pr.test.state === 'ok') pr.lastSignal = '۱ دقیقه';
        this.render();
      });
    },
    runSmsTest() { this.s.smsResult = parseSample(this.s.smsSample); this.render(); },
    finish() { this.s.onboarded = true; this.s.draft.isNew = false; this.stack = ['home']; this.render(false); },
    later() { this.s.onboarded = true; this.stack = ['home']; this.render(false); },
    resumeSetup(v) { this.s.cur.profile = v; this.stack = ['home', 'checklist']; this.render(false); },
    openProfile(v) { this.s.cur.profile = v; this.go('profile-detail'); },
    openUnparsed() { this.s.activityTab = 'unparsed'; this.stack = ['activity']; this.render(false); },
    newProfile() {
      const id = 'p' + (this.s.profiles.length + 1) + 'n';
      Object.assign(this.s.draft, { name: 'پروفایل ' + fn(this.s.profiles.length + 1), service: 'asancart', customUrl: '', isNew: true, profileId: id });
      this.go('profile');
    },
    toggleHb() { const h = prof(this.s).heartbeat; h.on = !h.on; if (h.on && !h.interval) h.interval = 300; this.render(); },
    setHb(v) { prof(this.s).heartbeat.interval = +v; this.render(); },
    flush(v) {
      const pr = prof(this.s, v);
      this.toast(pr.lastError ? 'هنوز وصل نمی‌شود. خودکار دوباره تلاش می‌کنیم.' : 'صف خالی شد');
    },
    askRemove() { this.ui.sheet = 'remove'; this.render(); },
    removeProfile() {
      const pr = prof(this.s);
      this.s.profiles = this.s.profiles.filter((x) => x !== pr);
      this.s.cur.profile = (this.s.profiles[0] || {}).id;
      this.ui.sheet = null;
      if (!this.s.profiles.length) { this.reset(); return; }
      this.stack = ['home']; this.render(false);
      this.toast('پروفایل حذف شد');
    },
    actTab(v) { this.s.activityTab = v; this.render(false); },
    openSms(v) { this.s.cur.sms = v; this.go('sms-detail'); },
    retrySms() { this.toast('قالب‌ها به‌روز است؛ هنوز قالبی این پیامک را نمی‌خواند'); },
    tplFromSms() {
      const u = this.s.unparsed.find((x) => x.id === this.s.cur.sms);
      Object.assign(this.s.tpl, { name: 'my-' + CARDS[u.card].bank + '-transfer', senders: u.sender, sample: u.text, pattern: '^', result: null });
      this.go('template-edit');
    },
    dismissSms() {
      this.s.unparsed = this.s.unparsed.filter((x) => x.id !== this.s.cur.sms);
      this.back();
      this.toast('پیامک از فهرست حذف شد');
    },
    refreshTpl() { this.toast('۸ قالب رسمی به‌روز است'); },
    newTpl() { Object.assign(this.s.tpl, { name: 'blu-deposit', senders: '90000258, +989999987641', pattern: CUSTOM_PATTERN, sample: CUSTOM_SAMPLE, result: null }); this.go('template-edit'); },
    tplMode(v) { this.s.tpl.mode = v; this.render(); },
    tplUnit(v) { this.s.tpl.unit = v; this.render(); },
    tplTest() { this.s.tpl.result = parseSample(this.s.tpl.sample); this.render(); },
    saveTpl() {
      const t = this.s.tpl;
      if (!this.s.custom.find((x) => x.name === t.name)) this.s.custom.push({ id: 'k' + (this.s.custom.length + 1), name: t.name, bankName: 'سفارشی', v: 1, dir: 'in' });
      this.back();
      this.toast('قالب ذخیره شد');
    },
    setTheme(v) { this.o.theme = v; if (this.o.onTheme) this.o.onTheme(v); this.render(); },
    toggleAutoTpl() { this.s.autoTemplates = !this.s.autoTemplates; this.render(); },
    toast(v) { this.toast(v); },
  };

  function renderStatic(id, o) {
    const sc = SCREENS[id];
    const p = new Proto(Object.assign({}, o, { interactive: false, preset: sc.galleryPreset || sc.preset, screen: id }));
    if (id === 'listener') p.s.cur.card = 'c2';
    if (sc.galleryPreset === 'tested') p.s.smsResult = parseSample(p.s.smsSample);
    if (id === 'template-edit') p.s.tpl.result = parseSample(p.s.tpl.sample);
    return p.html();
  }

  global.ACProto = { create: (o) => new Proto(o), renderStatic, SCREENS, ORDER };
})(window);
