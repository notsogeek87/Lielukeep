#!/usr/bin/env bash
# Crée la clé de signature de publication de LieluKeep (hors dépôt) et prépare les 4 secrets GitHub attendus par la CI.
#
# Usage :   scripts/generate-signing-key.sh [dossier-de-sortie]     (défaut : ~/lielukeep-signing)
#
# - La clé privée n'est JAMAIS écrite dans le dépôt. Sauvegardez le dossier de sortie (gestionnaire de mots de passe) :
#   perdre la clé = impossible de mettre à jour l'app sans la désinstaller.
# - Les secrets ne sont pas envoyés automatiquement : le script affiche où les saisir.
set -euo pipefail

OUT="${1:-$HOME/lielukeep-signing}"
REPO="notsogeek87/Lielukeep"
ALIAS="lielukeep"

command -v keytool >/dev/null || { echo "keytool introuvable (installer un JDK 17)." >&2; exit 1; }
if [ -e "$OUT/lielukeep-release.jks" ]; then
    echo "Une clé existe déjà dans $OUT : rien n'est écrasé. Supprimez-la volontairement pour en recréer une." >&2
    exit 1
fi

mkdir -p "$OUT"
chmod 700 "$OUT"
PASSWORD="$(head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | head -c 24)"

keytool -genkeypair -v \
    -keystore "$OUT/lielukeep-release.jks" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
    -storepass "$PASSWORD" -keypass "$PASSWORD" \
    -dname "CN=LieluKeep, O=Lielu, C=FR" >/dev/null

base64 -w0 "$OUT/lielukeep-release.jks" > "$OUT/KEYSTORE_BASE64.txt"
printf '%s' "$PASSWORD" > "$OUT/KEY_PASSWORD.txt"
chmod 600 "$OUT"/*

echo "Clé créée dans $OUT (à sauvegarder hors de cette machine)."
echo
echo "Créez 4 secrets d'Actions : https://github.com/$REPO/settings/secrets/actions/new"
echo "  KEYSTORE_BASE64   = contenu de $OUT/KEYSTORE_BASE64.txt"
echo "  KEYSTORE_PASSWORD = contenu de $OUT/KEY_PASSWORD.txt"
echo "  KEY_ALIAS         = $ALIAS"
echo "  KEY_PASSWORD      = contenu de $OUT/KEY_PASSWORD.txt (même valeur)"
