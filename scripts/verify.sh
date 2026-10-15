#!/usr/bin/env bash
# Vérification avant push : lance TOUT ce qui est exécutable dans l'environnement courant.
#
# Usage :
#   scripts/verify.sh            # vérifications statiques + Gradle (tests unitaires, APK debug et release)
#   scripts/verify.sh --fast     # seulement les vérifications statiques (pas de Gradle)
#
# Variables d'environnement :
#   LIELU_SKIP_GRADLE=1        # équivaut à --fast
#
# Sortie 0 = rien à signaler. Sortie 1 = au moins une vérification a échoué : ne pas pousser.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

FAST=0
[ "${1:-}" = "--fast" ] && FAST=1
[ "${LIELU_SKIP_GRADLE:-}" = "1" ] && FAST=1

failures=0
skipped_gradle=0

ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
ko() { printf '  \033[31m✗\033[0m %s\n' "$1"; failures=$((failures + 1)); }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
title() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# ---------------------------------------------------------------------------
title "1/4 Marqueurs de conflit de merge non résolus"
if conflicts="$(git grep -n -E '^(<<<<<<< |>>>>>>> )' -- '*.kt' '*.kts' '*.xml' '*.toml' '*.md' '*.yml' 2>/dev/null)"; then
    echo "$conflicts" | head -20
    ko "marqueurs de conflit présents dans des fichiers suivis"
else
    ok "aucun marqueur de conflit"
fi

# ---------------------------------------------------------------------------
title "2/4 Aucune couleur en dur dans l'interface (tout passe par le thème)"
if colors="$(grep -rnE 'Color\((0x|[0-9])|Color\.(Red|Blue|Green|Black|White|Gray|Grey|Yellow|Cyan|Magenta)\b|parseColor|"#[0-9A-Fa-f]{6,8}"' app/src/main/java 2>/dev/null)"; then
    echo "$colors" | head -20
    ko "couleurs en dur : utiliser MaterialTheme.colorScheme"
else
    ok "aucune couleur en dur"
fi

# ---------------------------------------------------------------------------
title "3/4 Références au version catalog (libs.versions.toml)"
python3 - <<'PY'
import re, sys, pathlib

toml = pathlib.Path('gradle/libs.versions.toml')
if not toml.exists():
    print("  ! gradle/libs.versions.toml introuvable — vérification ignorée")
    sys.exit(0)

section, keys = None, set()
for line in toml.read_text().splitlines():
    line = line.strip()
    if line.startswith('['):
        section = line.strip('[]')
        continue
    m = re.match(r'([A-Za-z0-9_.\-]+)\s*=', line)
    if not m or section is None:
        continue
    alias = m.group(1).replace('-', '.').replace('_', '.')
    prefix = {'versions': 'versions.', 'plugins': 'plugins.', 'bundles': 'bundles.'}.get(section, '')
    keys.add(prefix + alias)

unknown = []
for path in list(pathlib.Path('.').glob('*.gradle.kts')) + list(pathlib.Path('app').glob('*.gradle.kts')):
    for ref in re.finditer(r'\blibs\.([A-Za-z0-9_.]+)(\()?', path.read_text()):
        if ref.group(2):
            continue
        accessor = ref.group(1).rstrip('.')
        if accessor in keys or any(k.startswith(accessor + '.') for k in keys):
            continue
        unknown.append(f"{path}: libs.{accessor}")

if unknown:
    for u in unknown:
        print(f"  ✗ alias inconnu → {u}")
    sys.exit(1)

print(f"  ✓ tous les alias libs.* référencés existent ({len(keys)} alias déclarés)")
PY
[ $? -ne 0 ] && failures=$((failures + 1))

# ---------------------------------------------------------------------------
title "4/4 Compilation, tests unitaires et APK (Gradle)"
gradle_reachable() {
    # Google Maven (dl.google.com) est nécessaire pour résoudre le plugin Android Gradle et installer le SDK.
    curl -s -o /dev/null --max-time 10 "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
}

if [ "$FAST" = "1" ]; then
    warn "Gradle ignoré (--fast) : compilation et tests NON vérifiés ici"
    skipped_gradle=1
elif [ ! -f ./gradlew ]; then
    warn "gradlew introuvable — compilation et tests NON vérifiés ici"
    skipped_gradle=1
elif ! gradle_reachable; then
    warn "dl.google.com injoignable (politique réseau) : Gradle ne peut pas résoudre le plugin Android."
    warn "Compilation et tests unitaires NON vérifiés ici — c'est la CI de la branche qui les valide."
    skipped_gradle=1
elif [ -z "${ANDROID_HOME:-}" ] && [ ! -f local.properties ] && ! sdk="$(scripts/setup-android-sdk.sh)"; then
    warn "SDK Android introuvable et installation impossible (scripts/setup-android-sdk.sh) : Gradle ne peut pas tourner."
    skipped_gradle=1
else
    [ -n "${sdk:-}" ] && export ANDROID_HOME="$sdk"
    if ./gradlew --no-daemon testDebugUnitTest assembleDebug assembleRelease; then
        ok "Gradle : tests unitaires, APK debug et release OK"
    else
        ko "Gradle : voir la sortie ci-dessus"
    fi
fi

# ---------------------------------------------------------------------------
echo
if [ "$failures" -gt 0 ]; then
    printf '\033[31m✗ %s vérification(s) en échec — ne pas pousser en l'"'"'état.\033[0m\n' "$failures"
    exit 1
fi

if [ "$skipped_gradle" = "1" ]; then
    printf '\033[33m✓ Vérifications statiques OK, mais compilation/tests non exécutés ici.\033[0m\n'
    printf '  Pousser la branche de travail et attendre le vert de la CI avant de merger dans main.\n'
else
    printf '\033[32m✓ Tout est vert.\033[0m\n'
fi
