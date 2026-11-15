// Checks the REQ-DEV-041 dialect cases (core testFixtures regex-dialect-cases.json) against
// JavaScript RegExp without the `u` flag, the admin engine's regex library, so the expectations
// the Device engine is tested with are the admin engine's.
//   allowed: compiles; each `match` text is a full match after REQ-DEV-050's always-on steps (it
//            matches ^(?:P)$, as the admin engine's compileFullMatch), with the listed named groups;
//            no `noMatch` text is.
//   regex:   RegExp refuses it.
//   refused: not checked here (the dialect check is the engines' own); listed with whether
//            RegExp alone would compile it.
// Usage: node scripts/check-dialect-cases.mjs [cases.json]
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");
const file =
  process.argv[2] ??
  path.join(root, "core/src/testFixtures/resources/io/github/asancart/android/core/template/fixtures/regex-dialect-cases.json");
const cases = JSON.parse(fs.readFileSync(file, "utf8"));

// REQ-DEV-050, first and always: line breaks to \n, the listed whitespace to a space, U+FEFF removed,
// a character outside the Basic Multilingual Plane or a lone surrogate to U+FFFD.
const alwaysOn = (s) =>
  s
    .replace(/\r\n|[\r\u0085\u2028\u2029]/g, "\n")
    .replace(/[\u000B\u000C\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]/g, " ")
    .replace(/\uFEFF/g, "")
    .replace(/[\uD800-\uDBFF][\uDC00-\uDFFF]|[\uD800-\uDFFF]/g, "\uFFFD");

const show = (s) => JSON.stringify(s);
const failures = [];
const full = (re, text) => re.exec(text);

for (const c of cases.allowed) {
  let re;
  try {
    new RegExp(c.pattern, c.flags ?? "");
    re = new RegExp(`^(?:${c.pattern})$`, c.flags ?? "");
  } catch (e) {
    failures.push(`allowed ${show(c.pattern)}: RegExp refuses it: ${e.message}`);
    continue;
  }
  for (const t of c.match) if (!full(re, alwaysOn(t))) failures.push(`allowed ${show(c.pattern)}: ${show(t)} should match`);
  for (const t of c.noMatch) if (full(re, alwaysOn(t))) failures.push(`allowed ${show(c.pattern)}: ${show(t)} should not match`);
  for (const g of c.groups ?? []) {
    const m = full(re, alwaysOn(g.text));
    if (!m) continue;
    for (const [k, v] of Object.entries(g.named)) {
      const have = m.groups?.[k] ?? null;
      if (have !== v) failures.push(`allowed ${show(c.pattern)} on ${show(g.text)}: group ${k} is ${show(have)}, expected ${show(v)}`);
    }
  }
}

for (const c of cases.regex) {
  try {
    new RegExp(c.pattern);
    failures.push(`regex ${show(c.pattern)}: RegExp compiles it (${c.why})`);
  } catch {
    // expected
  }
}

let compiles = 0;
for (const c of cases.refused) {
  try {
    new RegExp(c.pattern);
    compiles++;
  } catch {
    // RegExp refuses it on its own; the dialect check refuses it first anyway.
  }
}

console.log(
  `allowed ${cases.allowed.length}, regex ${cases.regex.length}, refused ${cases.refused.length} (${compiles} of them RegExp alone would compile)`,
);
if (failures.length) {
  console.log(failures.join("\n"));
  process.exit(1);
}
console.log("dialect cases agree with JavaScript RegExp");
