// Checks the REQ-DEV-041 dialect cases (core testFixtures regex-dialect-cases.json) against
// JavaScript RegExp without the `u` flag, the admin engine's regex library, so the expectations
// the Device engine is tested with are the admin engine's.
//   allowed: compiles; each `match` text is a full match after REQ-DEV-050's always-on steps (it
//            matches ^(?:P)$, as the admin engine's compileFullMatch), with the listed named groups,
//            whose texts must match (T4.24);
//            no `noMatch` text is. Flag i folds ASCII letters only (T2.18): the pattern is rewritten
//            (k to [kK], [a-f] to [a-fA-F]) and compiled without RegExp's own i.
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

// REQ-DEV-041 flag i (T2.18): ASCII letters only. Outside a class an ASCII letter, or a \xhh or
// \uhhhh naming one, becomes a class of both cases; a class gets the other case of every ASCII
// letter it holds, ranges included. Patterns here are inside the dialect.
const otherCase = (c) => (c >= "a" && c <= "z" ? c.toUpperCase() : c >= "A" && c <= "Z" ? c.toLowerCase() : null);
const HEX = /^\\(?:x([0-9A-Fa-f]{2})|u([0-9A-Fa-f]{4}))/;
/** The atom at i: its source text, the one character it stands for (null for \d \D \s \S), its length. */
function atom(p, i) {
  if (p[i] !== "\\") return { text: p[i], ch: p[i], len: 1 };
  const m = HEX.exec(p.slice(i));
  if (m) return { text: m[0], ch: String.fromCharCode(parseInt(m[1] ?? m[2], 16)), len: m[0].length };
  const e = p[i + 1];
  if ("dDsS".includes(e)) return { text: p.slice(i, i + 2), ch: null, len: 2 };
  return { text: p.slice(i, i + 2), ch: e === "n" ? "\n" : e === "t" ? "\t" : e, len: 2 };
}
function foldAscii(p) {
  let out = "";
  let i = 0;
  while (i < p.length) {
    if (p[i] === "[") {
      let j = i + 1;
      let head = "[";
      if (p[j] === "^") {
        head += "^";
        j++;
      }
      let body = "";
      let extra = "";
      while (j < p.length && p[j] !== "]") {
        const a = atom(p, j);
        j += a.len;
        if (a.ch !== null && p[j] === "-" && j + 1 < p.length && p[j + 1] !== "]") {
          const b = atom(p, j + 1);
          if (b.ch !== null) {
            j += 1 + b.len;
            body += a.text + "-" + b.text;
            for (let k = a.ch.charCodeAt(0); k <= b.ch.charCodeAt(0); k++) extra += otherCase(String.fromCharCode(k)) ?? "";
            continue;
          }
        }
        body += a.text;
        if (a.ch !== null) extra += otherCase(a.ch) ?? "";
      }
      out += head + body + extra + "]";
      i = j + 1;
      continue;
    }
    // Group openers and brace quantifiers hold letters and digits that aren't literals.
    const opener = /^(?:\(\?(?:[:=!]|<[A-Za-z][A-Za-z0-9]*>)|\{\d+(?:,\d*)?\})/.exec(p.slice(i));
    if (opener) {
      out += opener[0];
      i += opener[0].length;
      continue;
    }
    const a = atom(p, i);
    const o = a.ch !== null && a.text !== "." ? otherCase(a.ch) : null;
    out += o ? `[${a.ch}${o}]` : a.text;
    i += a.len;
  }
  return out;
}

const show = (s) => JSON.stringify(s);
const failures = [];
const full = (re, text) => re.exec(text);

for (const c of cases.allowed) {
  let re;
  try {
    const flags = c.flags ?? "";
    new RegExp(c.pattern, flags);
    const source = flags.includes("i") ? foldAscii(c.pattern) : c.pattern;
    re = new RegExp(`^(?:${source})$`, flags.replace("i", ""));
  } catch (e) {
    failures.push(`allowed ${show(c.pattern)}: RegExp refuses it: ${e.message}`);
    continue;
  }
  for (const t of c.match) if (!full(re, alwaysOn(t))) failures.push(`allowed ${show(c.pattern)}: ${show(t)} should match`);
  for (const t of c.noMatch) if (full(re, alwaysOn(t))) failures.push(`allowed ${show(c.pattern)}: ${show(t)} should not match`);
  for (const g of c.groups ?? []) {
    // T4.24: a groups text must match; one that doesn't fails, and its groups compare as null.
    const m = full(re, alwaysOn(g.text));
    if (!m) failures.push(`allowed ${show(c.pattern)}: groups text ${show(g.text)} doesn't match`);
    for (const [k, v] of Object.entries(g.named)) {
      const have = m?.groups?.[k] ?? null;
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
