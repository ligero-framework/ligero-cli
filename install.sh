#!/usr/bin/env sh
# Ligero CLI installer for macOS / Linux.
#   curl -fsSL https://github.com/ligero-framework/ligero-cli/releases/latest/download/install.sh | sh
# Downloads the native `ligero` binary for your OS/arch (no JVM required),
# installs it to ~/.ligero/bin, and adds that directory to your PATH.
set -eu

REPO="ligero-framework/ligero-cli"
VERSION="${LIGERO_VERSION:-latest}"
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

echo "Installing Ligero CLI ($asset)…"
mkdir -p "$INSTALL_DIR"
if command -v curl >/dev/null 2>&1; then
  curl -fsSL "$url" -o "$INSTALL_DIR/ligero"
elif command -v wget >/dev/null 2>&1; then
  wget -qO "$INSTALL_DIR/ligero" "$url"
else
  echo "Need curl or wget to download." >&2; exit 1
fi
chmod +x "$INSTALL_DIR/ligero"

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
