#!/usr/bin/env sh
# Ligero CLI installer for macOS / Linux.
#   curl -fsSL https://github.com/ligero-framework/ligero-cli/releases/latest/download/install.sh | sh
# Downloads the native `ligero` binary for your OS/arch (no JVM required),
# verifies its SHA-256 against the checksum published with the release,
# installs it to ~/.ligero/bin, and adds that directory to your PATH.
#   LIGERO_VERSION=v0.2.0       install a specific release (default: latest)
#   LIGERO_REQUIRE_CHECKSUM=1   refuse to install when no checksum is published
set -eu

REPO="ligero-framework/ligero-cli"
VERSION="${LIGERO_VERSION:-latest}"
case "$VERSION" in
  latest) ;;
  *[!A-Za-z0-9._-]*) echo "Invalid LIGERO_VERSION: $VERSION" >&2; exit 1 ;;
esac
INSTALL_DIR="${LIGERO_HOME:-$HOME/.ligero}/bin"

os="$(uname -s)"
arch="$(uname -m)"
case "$os" in
  Linux)
    case "$arch" in
      x86_64|amd64) asset="ligero-linux-x64" ;;
      aarch64|arm64) asset="ligero-linux-arm64" ;;
      *) echo "Unsupported architecture: $arch" >&2; exit 1 ;;
    esac ;;
  Darwin)
    case "$arch" in
      arm64) asset="ligero-macos-arm64" ;;
      x86_64) asset="ligero-macos-x64" ;;
      *) echo "Unsupported architecture: $arch" >&2; exit 1 ;;
    esac ;;
  *) echo "Unsupported OS: $os (use install.ps1 on Windows)" >&2; exit 1 ;;
esac

if [ "$VERSION" = "latest" ]; then
  url="https://github.com/$REPO/releases/latest/download/$asset"
else
  url="https://github.com/$REPO/releases/download/$VERSION/$asset"
fi

# fetch <url> <destination>; HTTPS only, even across redirects
fetch() {
  if command -v curl >/dev/null 2>&1; then
    curl --proto '=https' --tlsv1.2 -fsSL "$1" -o "$2"
  elif command -v wget >/dev/null 2>&1; then
    wget --https-only -qO "$2" "$1"
  else
    echo "Need curl or wget to download." >&2; exit 1
  fi
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    echo ""
  fi
}

echo "Installing Ligero CLI ($asset)…"
mkdir -p "$INSTALL_DIR"
# Download next to the destination and only move it into place once it has
# been verified, so a failed or tampered download never becomes `ligero`.
tmp="$(mktemp "$INSTALL_DIR/.ligero.XXXXXX")"
trap 'rm -f "$tmp" "$tmp.sha256"' EXIT INT TERM
fetch "$url" "$tmp"

# Integrity: releases publish <asset>.sha256 next to each binary.
if fetch "$url.sha256" "$tmp.sha256" 2>/dev/null; then
  expected="$(cut -d' ' -f1 < "$tmp.sha256" | tr -d '[:space:]')"
  actual="$(sha256_of "$tmp")"
  if [ -z "$actual" ]; then
    echo "Warning: no sha256sum/shasum found — skipping checksum verification." >&2
  elif [ "$expected" != "$actual" ]; then
    echo "Checksum mismatch for $asset" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    echo "The download is corrupt or has been tampered with; nothing was installed." >&2
    exit 1
  else
    echo "✔ Checksum verified"
  fi
elif [ "${LIGERO_REQUIRE_CHECKSUM:-0}" = "1" ]; then
  echo "No checksum published for $asset and LIGERO_REQUIRE_CHECKSUM=1 — aborting." >&2
  exit 1
else
  echo "Warning: this release publishes no checksum for $asset — installing unverified." >&2
fi

chmod +x "$tmp"
mv -f "$tmp" "$INSTALL_DIR/ligero"

# Add to PATH in the user's shell rc files (idempotent).
line="export PATH=\"$INSTALL_DIR:\$PATH\""
added=""
for rc in "$HOME/.zshrc" "$HOME/.bashrc" "$HOME/.profile"; do
  [ -f "$rc" ] || continue
  if ! grep -qs "\.ligero/bin" "$rc"; then
    printf '\n# Ligero CLI\n%s\n' "$line" >> "$rc"
    added="$added $rc"
  fi
done

echo ""
echo "✔ Installed to $INSTALL_DIR/ligero"
[ -n "$added" ] && echo "✔ Added to PATH in:$added"
echo ""
echo "Open a new terminal (or run 'export PATH=\"$INSTALL_DIR:\$PATH\"'), then:"
echo "  ligero version"
echo "  ligero new my-api"
