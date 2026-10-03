#!/bin/sh
set -eu

repository="JAIPilot/jaipilot"
command -v curl >/dev/null 2>&1 || { echo "Install curl first." >&2; exit 1; }
case "$(uname -s)" in
  Darwin) platform="apple-darwin" ;;
  Linux) platform="unknown-linux-gnu" ;;
  *) echo "This installer supports macOS and Linux. See the README for Windows." >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64|amd64) architecture="x86_64" ;;
  arm64|aarch64) architecture="aarch64" ;;
  *) echo "Unsupported CPU architecture: $(uname -m)" >&2; exit 1 ;;
esac

version="${JAIPILOT_VERSION:-}"
if [ -z "$version" ]; then
  latest=$(curl -fsSL --connect-timeout 10 --max-time 30 -o /dev/null -w '%{url_effective}' \
    "https://github.com/$repository/releases/latest")
  version="${latest##*/}"
fi
case "$version" in v*) ;; *) version="v$version" ;; esac
printf '%s\n' "$version" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$' || {
  echo "Could not determine a stable JAIPilot release." >&2; exit 1;
}

install_dir="${JAIPILOT_INSTALL_DIR:-$HOME/.local/bin}"
mkdir -p "$install_dir"
temporary=$(mktemp -d "$install_dir/.jaipilot-install.XXXXXX")
trap 'exit_status=$?; rm -rf "$temporary"; exit "$exit_status"' 0
trap 'exit 1' HUP INT TERM
asset="jaipilot-$architecture-$platform"
base="https://github.com/$repository/releases/download/$version"
echo "Installing JAIPilot CLI $version for $architecture-${platform}..." >&2
curl -fSL --retry 2 --connect-timeout 10 --max-time 180 \
  "$base/$asset" -o "$temporary/jaipilot"
curl -fsSL --retry 2 --connect-timeout 10 --max-time 30 \
  "$base/$asset.sha256" -o "$temporary/checksum"
expected=$(awk 'NR == 1 { print $1 }' "$temporary/checksum")
printf '%s\n' "$expected" | grep -Eq '^[0-9a-f]{64}$' || {
  echo "Invalid release checksum. Existing installation unchanged." >&2; exit 1;
}
if command -v sha256sum >/dev/null 2>&1; then
  actual=$(sha256sum "$temporary/jaipilot" | awk '{ print $1 }')
elif command -v shasum >/dev/null 2>&1; then
  actual=$(shasum -a 256 "$temporary/jaipilot" | awk '{ print $1 }')
else
  echo "Install sha256sum or shasum to verify the download." >&2; exit 1
fi
[ "$actual" = "$expected" ] || {
  echo "Checksum mismatch. Existing installation unchanged." >&2; exit 1;
}
chmod 755 "$temporary/jaipilot"
[ "$("$temporary/jaipilot" --version)" = "JAIPilot CLI ${version#v}" ] || {
  echo "Downloaded CLI failed its version check. Existing installation unchanged." >&2; exit 1;
}
mv -f "$temporary/jaipilot" "$install_dir/jaipilot"
echo "Installed $install_dir/jaipilot" >&2
case ":${PATH:-}:" in
  *":$install_dir:"*) ;;
  *) printf 'Add this directory to your PATH:\n  export PATH="%s:$PATH"\n' "$install_dir" >&2 ;;
esac
echo "Run: jaipilot auth login" >&2
