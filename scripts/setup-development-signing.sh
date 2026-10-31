#!/usr/bin/env bash
# Create a long-lived Occultation development signer and store it as GitHub Actions secrets.
set -euo pipefail

repo="NotADeveloper4665/Occultation"
alias_name="occultationdev"
data_dir="${XDG_DATA_HOME:-$HOME/.local/share}/occultation/development-signing"
keystore="$data_dir/occultation-development.keystore"
password_file="$data_dir/keystore-password"

for command in gh keytool openssl base64; do
    command -v "$command" >/dev/null 2>&1 || {
        echo "Missing required command: $command" >&2
        exit 1
    }
done

gh auth status --hostname github.com >/dev/null
if [[ -e "$keystore" || -e "$password_file" ]]; then
    echo "A local development signing key already exists at: $data_dir" >&2
    echo "Refusing to overwrite it. Preserve this key and its password for recovery." >&2
    exit 1
fi

umask 077
mkdir -p "$data_dir"
password="$(openssl rand -hex 32)"
keytool -genkeypair \
    -keystore "$keystore" \
    -storepass "$password" \
    -alias "$alias_name" \
    -keypass "$password" \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Occultation Development,O=Occultation,C=US'
printf '%s' "$password" > "$password_file"
chmod 600 "$keystore" "$password_file"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
base64 -w0 "$keystore" > "$tmp/keystore.b64"

gh secret set OCCULTATION_DEV_KEYSTORE_BASE64 --repo "$repo" < "$tmp/keystore.b64"
printf '%s' "$password" | gh secret set OCCULTATION_DEV_KEYSTORE_PASSWORD --repo "$repo"
printf '%s' "$alias_name" | gh secret set OCCULTATION_DEV_KEY_ALIAS --repo "$repo"
printf '%s' "$password" | gh secret set OCCULTATION_DEV_KEY_PASSWORD --repo "$repo"

# Preserve the old public signer pin for audit, then remove the active pin.
# The next moonlight-noir build will publish the new fingerprint from the new key.
if gh release view dev --repo "$repo" --json assets --jq '.assets[].name' > "$tmp/assets.txt" 2>/dev/null \
    && grep -qx 'development-signer.sha256' "$tmp/assets.txt"; then
    gh release download dev --repo "$repo" --pattern development-signer.sha256 --dir "$tmp"
    cp "$tmp/development-signer.sha256" "$tmp/development-signer.previous.sha256"
    gh release upload dev "$tmp/development-signer.previous.sha256" --repo "$repo" --clobber
    gh release delete-asset dev development-signer.sha256 --repo "$repo" --yes
fi

echo "Development signing key created and stored in repository Actions secrets."
echo "Local recovery copy: $keystore"
echo "Local password copy: $password_file"
echo "Back up both files together in a secure password manager or encrypted backup."
echo "Next: merge the signing-fix PR, then run the Occultation Android workflow on moonlight-noir"
echo "with rotate_development_signing_key=true exactly once."
