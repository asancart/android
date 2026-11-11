// Inline the prototype CSS/JS into self-contained pages.
//
//   node design/build.mjs                 -> design/dist/wireframe.html, design/dist/mockups.html
//   node design/build.mjs --fragment DIR  -> also writes body-only copies to DIR (for hosts that add their own <html>/<head>)
//   XLINK_wireframe=URL XLINK_mockups=URL -> rewrite the cross-links between the two pages
//
// No dependencies. The source pages work as-is when opened from design/ in a browser.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const pages = ['wireframe', 'mockups'];
const fragIdx = process.argv.indexOf('--fragment');
const fragDir = fragIdx > -1 ? process.argv[fragIdx + 1] : null;

function inline(html) {
  html = html.replace(/<link rel="stylesheet" href="(prototype\/[^"]+\.css)">/g, (_, p) => `<style>\n${readFileSync(join(here, p), 'utf8')}\n</style>`);
  html = html.replace(/<script src="(prototype\/[^"]+\.js)"><\/script>/g, (_, p) => `<script>\n${readFileSync(join(here, p), 'utf8').replace(/<\/script/gi, '<\\/script')}\n</script>`);
  for (const p of pages) {
    const url = process.env['XLINK_' + p];
    if (url) html = html.replace(new RegExp(`data-xlink="${p}" href="[^"]*"`, 'g'), `data-xlink="${p}" href="${url}" target="_blank" rel="noopener"`);
  }
  return html;
}

function fragment(html) {
  return html
    .replace(/<!doctype html>\s*/i, '')
    .replace(/<\/?html[^>]*>\s*/gi, '')
    .replace(/<\/?head>\s*/gi, '')
    .replace(/<\/?body>\s*/gi, '')
    .replace(/<meta charset="utf-8">\s*/i, '')
    .replace(/<meta name="viewport"[^>]*>\s*/i, '');
}

mkdirSync(join(here, 'dist'), { recursive: true });
if (fragDir) mkdirSync(fragDir, { recursive: true });
for (const p of pages) {
  const out = inline(readFileSync(join(here, p + '.html'), 'utf8')).replace(/href="(wireframe|mockups)\.html"/g, 'href="$1.html"');
  writeFileSync(join(here, 'dist', p + '.html'), out);
  if (fragDir) writeFileSync(join(fragDir, p + '.html'), fragment(out));
  console.log(`built ${p}.html (${(out.length / 1024).toFixed(0)} KB)`);
}
