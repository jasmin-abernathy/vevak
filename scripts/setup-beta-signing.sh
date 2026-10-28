#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRIVATE="$ROOT/.private"
KEYSTORE="$PRIVATE/vevak-beta-signing.p12"
PASSFILE="$PRIVATE/vevak-beta-signing.password"
ALIAS="vevak-beta"
REPO="jasmin-abernathy/vevak"

if ! command -v keytool >/dev/null || ! command -v openssl >/dev/null; then
  echo "Install JDK (keytool) and openssl first." >&2
  exit 1
fi

mkdir -p "$PRIVATE"
chmod 700 "$PRIVATE"
if [[ -e "$KEYSTORE" || -e "$PASSFILE" ]]; then
  if [[ ! -s "$KEYSTORE" || ! -s "$PASSFILE" ]]; then
    echo "Incomplete key material in .private/. Do not overwrite an existing signing identity." >&2
    exit 1
  fi
  echo "Reusing the existing beta signing key in .private/."
else
  PASSWORD="$(openssl rand -hex 24)"
  printf '%s' "$PASSWORD" > "$PASSFILE"
  keytool -genkeypair -noprompt \
    -storetype PKCS12 -keystore "$KEYSTORE" -storepass "$PASSWORD" \
    -alias "$ALIAS" -keypass "$PASSWORD" \
    -keyalg RSA -keysize 3072 -validity 7300 \
    -dname "CN=VeVak Beta Tester, OU=Development, O=Le Potager du Web, C=FR"
  unset PASSWORD
  chmod 600 "$KEYSTORE" "$PASSFILE"
  echo "Created tester-only signing identity."
fi

PASSWORD="$(cat "$PASSFILE")"
FINGERPRINT="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias "$ALIAS" \
  | sed -n 's/.*SHA256: //p' | head -n 1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
if [[ ! "$FINGERPRINT" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Failed to read signing certificate SHA-256." >&2
  exit 1
fi
echo "Signing certificate SHA-256: $FINGERPRINT"
echo "Back up the .private/ directory securely OFF GitHub and never share its contents."

if [[ "${1:-}" != "--upload" ]]; then
  echo "When ready, run: bash scripts/setup-beta-signing.sh --upload"
  exit 0
fi
if ! command -v gh >/dev/null; then
  echo "Install GitHub CLI (gh) and authenticate with gh auth login." >&2
  exit 1
fi
gh auth status >/dev/null
# Stream secrets via stdin: never commit or echo credentials.
base64 < "$KEYSTORE" | tr -d '\r\n' | gh secret set -R "$REPO" VEVAK_BETA_KEYSTORE_BASE64
printf '%s' "$PASSWORD" | gh secret set -R "$REPO" VEVAK_BETA_STORE_PASSWORD
printf '%s' "$PASSWORD" | gh secret set -R "$REPO" VEVAK_BETA_KEY_PASSWORD
printf '%s' "$ALIAS" | gh secret set -R "$REPO" VEVAK_BETA_KEY_ALIAS
printf '%s' "$FINGERPRINT" | gh secret set -R "$REPO" VEVAK_BETA_CERT_SHA256
unset PASSWORD
echo "Protected GitHub Actions secrets configured. No private key entered the Git repository."
echo "Next: gh workflow run android-ci.yml -R $REPO --ref feature/cellular-fallback-local-memory -f signed_beta_test=true"
