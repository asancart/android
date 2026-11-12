#!/usr/bin/env bash
# REQ-APP-041: fail when a merged manifest declares anything that reaches Google Play Services or
# Firebase at runtime, even with no such class in the dex: the emoji2 startup initializer (asks the
# system downloadable-font provider, which is Play Services on most phones), GMS/Firebase
# components, FCM, Play Store install referrer or billing.
#
# Usage: scripts/check-manifest-no-gms.sh FILE...
#   FILE is a merged AndroidManifest.xml or an `aapt2 dump xmltree --file AndroidManifest.xml` dump.
set -uo pipefail

forbidden='androidx\.emoji2\.text\.EmojiCompatInitializer|androidx\.content\.action\.LOAD_EMOJI_FONT|com\.google\.android\.gms|com\.google\.firebase|com\.google\.android\.c2dm|com\.google\.android\.finsky|com\.android\.vending'

[ "$#" -gt 0 ] || { echo "usage: $0 FILE..." >&2; exit 2; }
status=0
for f in "$@"; do
  if [ ! -s "$f" ]; then
    echo "FAIL $f: missing or empty"; status=1; continue
  fi
  # Guard against checking the wrong file: it must be this app's manifest.
  if ! grep -q 'io\.github\.asancart\.android' "$f"; then
    echo "FAIL $f: not the Asancart app manifest"; status=1; continue
  fi
  # Only real entries count: XML comments are blanked (newlines kept, so line numbers still match).
  if hits=$(perl -0pe 's/<!--.*?-->/my $c = $&; $c =~ tr{\n}{}cd; $c/gse' "$f" | grep -nE "$forbidden"); then
    echo "FAIL $f: REQ-APP-041, entries that reach Google Play Services or Firebase:"
    echo "$hits"
    status=1
  else
    echo "ok   $f"
  fi
done
exit $status
