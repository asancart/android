// Writes fixtures/parser-templates/oracle.json: what the reference engine (the admin panel's
// engine.ts, vendored in asancart/documents parser-templates/tools/engine) returns for every
// official template on every fixture text. The Kotlin port must agree on every pair
// (core TemplateOracleTest, and on device in the app's androidTest).
//
// Usage: node scripts/gen-template-oracle.mjs <documents>/parser-templates fixtures/parser-templates/oracle.json
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const [ptDir, out] = process.argv.slice(2);
if (!ptDir || !out) {
  console.error("usage: node scripts/gen-template-oracle.mjs <documents>/parser-templates <out.json>");
  process.exit(2);
}
const engine = await import(pathToFileURL(path.join(ptDir, "tools/engine/engine.ts")).href);
const { runTemplate, normalizeSms, normalizeSender } = engine;

const banks = fs
  .readdirSync(ptDir)
  .filter((f) => f.endsWith(".json"))
  .sort()
  .map((f) => JSON.parse(fs.readFileSync(path.join(ptDir, f), "utf8")));

const readme = fs.readFileSync(path.join(ptDir, "README.md"), "utf8");
const sec = readme.split(/^## /m).find((s) => s.startsWith("Negative fixtures (proposed)"));
const crossBank = sec ? JSON.parse(/```json\n([\s\S]*?)\n```/.exec(sec)[1]) : [];

const texts = [];
for (const d of banks) for (const fx of d.fixtures) texts.push({ id: `${d.bank.id}/${fx.id}`, sender: fx.sender, sms_text: fx.sms_text });
for (const fx of crossBank) texts.push({ id: `cross/${fx.id}`, sender: fx.sender, sms_text: fx.sms_text });

const templates = [];
for (const d of banks) for (const t of d.templates) templates.push({ bank: d.bank.id, name: t.name, body: t });

const fieldKeys = ["amount", "account", "reference", "kind", "channel"];
const pairs = [];
let nonNoMatch = 0;
templates.forEach((t, ti) => {
  texts.forEach((x, xi) => {
    // Sender left out: the regex and classification are what is compared. Sender matching is
    // compared separately below.
    const r = runTemplate(t.body, x.sms_text, {});
    if (r.status === "NO_MATCH") return;
    nonNoMatch++;
    const fields = {};
    for (const k of fieldKeys) if (r.fields && r.fields[k] !== undefined) fields[k] = r.fields[k];
    pairs.push({ t: ti, x: xi, status: r.status, fields });
  });
});

const senders = new Set();
for (const x of texts) if (x.sender) senders.add(x.sender);
for (const t of templates) for (const s of t.body.sender_patterns) senders.add(s);

const oracle = {
  about: "Reference engine output for every official template on every fixture text. Regenerate with scripts/gen-template-oracle.mjs.",
  templates: templates.map((t) => ({ bank: t.bank, name: t.name })),
  texts: texts.map((x) => ({ id: x.id, normalized: normalizeSms(x.sms_text) })),
  // Every (template, text) pair not listed here is NO_MATCH.
  results: pairs,
  senders: [...senders].sort().map((s) => ({ raw: s, normalized: normalizeSender(s) })),
};
fs.writeFileSync(out, JSON.stringify(oracle, null, 1) + "\n");
console.log(`templates ${templates.length}, texts ${texts.length}, pairs ${templates.length * texts.length}, non-NO_MATCH ${nonNoMatch}, senders ${senders.size}`);
