#!/usr/bin/env bash
# Copies the Parser Templates and their fixtures from a local checkout of the private Asancart
# documents repository, for local tests only. Nothing here is committed (.gitignore):
#   fixtures/parser-templates/<bank>.json   bank files (templates + fixtures), unchanged
#   fixtures/parser-templates/negatives.json the README "Negative fixtures (proposed)" block
#   fixtures/parser-templates/oracle.json    the reference engine's output on every pair
#   core/.../official-parser-templates.json  what the stub Device API serves (local demos)
# Usage: scripts/sync-parser-templates.sh <documents checkout>   (needs Node 22.6+ and Python 3)
set -euo pipefail
docs="${1:?usage: scripts/sync-parser-templates.sh <documents checkout>}"
src="$docs/parser-templates"
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/fixtures/parser-templates"
mkdir -p "$out"
cp "$src"/*.json "$out"/
python3 -I - "$src/README.md" "$out/negatives.json" <<'PY'
import json, re, sys
s = open(sys.argv[1], encoding="utf-8").read()
sec = [x for x in re.split(r"^## ", s, flags=re.M) if x.startswith("Negative fixtures (proposed)")][0]
data = json.loads(re.search(r"```json\n([\s\S]*?)\n```", sec).group(1))
with open(sys.argv[2], "w", encoding="utf-8") as fh:
    json.dump(data, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
PY
node --no-warnings "$root/scripts/gen-template-oracle.mjs" "$src" "$out/oracle.json"
python3 -I "$root/scripts/gen-official-templates.py" "$out" "$root/core/src/main/resources/io/github/asancart/android/core/deviceapi/official-parser-templates.json"
echo "synced from $src"
