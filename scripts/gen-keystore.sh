#!/usr/bin/env bash
# Generates the ONLY signing key this app will ever use. Losing it makes 覆盖升级 impossible.
#
# Password requirements (measured with keytool from JBR 25):
#   - at least 6 characters, counted as characters not bytes
#   - storepass and keypass MUST be identical: PKCS12 silently ignores a different -keypass
#   - prefer 16-32 alphanumeric chars; $ ! " \ and spaces survive three shell layers poorly
set -euo pipefail
OUT="${1:-release.keystore}"
ALIAS="${KEY_ALIAS:-ufm}"

if [ -f "$OUT" ]; then
  echo "refusing to overwrite $OUT" >&2
  exit 1
fi

if [ "${#KEYSTORE_PASSWORD}" -lt 6 ]; then
  echo "KEYSTORE_PASSWORD must be at least 6 characters (got ${#KEYSTORE_PASSWORD})" >&2
  exit 1
fi
if [ "$KEYSTORE_PASSWORD" != "${KEY_PASSWORD:-}" ]; then
  echo "KEYSTORE_PASSWORD and KEY_PASSWORD must be identical: PKCS12 ignores a different -keypass" >&2
  exit 1
fi

"$JAVA_HOME/bin/keytool" -genkeypair -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
  -dname "CN=uni-file-manager, OU=personal, O=kcx, C=CN"
echo "created $OUT — back it up outside the repo, then run:"
echo "  base64 -w0 $OUT   # paste into the GitHub secret KEYSTORE_BASE64"
echo "Also set KEYSTORE_PASSWORD and KEY_PASSWORD to the SAME value in GitHub Secrets."
