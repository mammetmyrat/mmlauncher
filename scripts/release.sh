#!/usr/bin/env bash
# Builds a signed release APK and prepares the GitHub Pages folder (docs/) for publishing.
#
#   scripts/release.sh --site https://<user>.github.io/mm-launcher \
#       [--out docs] [--notes-en "..."] [--notes-ru "..."] [--notes-tk "..."]
#
# Needs keystore.properties (see RELEASE.md). Bump versionCode/versionName in
# app/build.gradle.kts BEFORE running. The script does not commit or push anything.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SITE=""; OUT="docs"; N_EN=""; N_RU=""; N_TK=""
while [ $# -gt 0 ]; do
  case "$1" in
    --site) SITE="${2%/}"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --notes-en) N_EN="$2"; shift 2 ;;
    --notes-ru) N_RU="$2"; shift 2 ;;
    --notes-tk) N_TK="$2"; shift 2 ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

CONFIG=app/src/main/java/com/mmlauncher/tv/Config.kt
[ -f keystore.properties ] || { echo "keystore.properties not found. See RELEASE.md." >&2; exit 1; }

if [ -z "$SITE" ]; then
  if grep -q "YOUR-GITHUB-USER" "$CONFIG"; then
    echo "Pass --site https://<user>.github.io/<repo> (update address is still a placeholder)." >&2; exit 1
  fi
else
  python3 - "$CONFIG" "$SITE/update.json" <<'PY'
import re, sys
p, url = sys.argv[1], sys.argv[2]
s = open(p, encoding="utf-8").read()
s2 = re.sub(r'(DEFAULT_UPDATE_URL\s*=\s*)"[^"]*"', lambda m: f'{m.group(1)}"{url}"', s)
open(p, "w", encoding="utf-8").write(s2)
PY
fi

VERSION_CODE="$(grep -E 'versionCode *=' app/build.gradle.kts | head -1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')"
VERSION_NAME="$(grep -E 'versionName *=' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
echo "Building MM Launcher $VERSION_NAME (versionCode $VERSION_CODE)..."

./gradlew --console=plain assembleRelease >/dev/null
APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "Signed APK not found ($APK). Is keystore.properties correct?" >&2; exit 1; }

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
if [ -n "$APKSIGNER" ]; then
  "$APKSIGNER" verify --print-certs "$APK" | head -3
else
  echo "apksigner not found, signature not verified." >&2
fi

mkdir -p "$OUT"
cp "$APK" "$OUT/mm-launcher-$VERSION_NAME.apk"
cp "$APK" "$OUT/mm-launcher.apk"
SHA="$(shasum -a 256 "$APK" | cut -d' ' -f1)"

python3 - "$OUT" "$SITE" "$VERSION_CODE" "$VERSION_NAME" "$SHA" "$N_EN" "$N_RU" "$N_TK" <<'PY'
import json, os, shutil, sys
out, site, code, name, sha, n_en, n_ru, n_tk = sys.argv[1:9]
chains_src = "app/src/main/res/raw/settings_chains.json"
upd_path = os.path.join(out, "update.json")
upd = json.load(open(upd_path, encoding="utf-8")) if os.path.exists(upd_path) else {}
if not site:
    # reuse the address already present in update.json
    site = upd.get("apkUrl", "").rsplit("/", 1)[0]
upd["versionCode"] = int(code)
upd["versionName"] = name
upd["apkUrl"] = f"{site}/mm-launcher-{name}.apk"
upd["sha256"] = sha
notes = upd.get("notes", {})
for k, v in (("en", n_en), ("ru", n_ru), ("tk", n_tk)):
    if v: notes[k] = v
upd["notes"] = notes

# chains config: bump its version only if the content changed
chains_dst = os.path.join(out, "settings_chains.json")
chains = upd.get("chains", {"version": 0})
if (not os.path.exists(chains_dst)) or open(chains_dst, "rb").read() != open(chains_src, "rb").read():
    chains["version"] = int(chains.get("version", 0)) + 1
    shutil.copyfile(chains_src, chains_dst)
chains["url"] = f"{site}/settings_chains.json"
upd["chains"] = chains

json.dump(upd, open(upd_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
open(upd_path, "a").write("\n")
print(f"update.json: versionCode={code} versionName={name} chains.version={chains['version']}")
PY

echo
echo "APK:     $OUT/mm-launcher-$VERSION_NAME.apk"
echo "SHA-256: $SHA"
echo "Next: review $OUT/update.json, commit and push $OUT/ (GitHub Pages)."
