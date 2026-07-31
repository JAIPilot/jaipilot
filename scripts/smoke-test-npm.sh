#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
DIST_DIR="$REPO_ROOT/target/distributions"
SMOKE_DIR="$REPO_ROOT/target/smoke-npm"
VERSION=""
CLASSIFIER=""

usage() {
  cat <<'EOF'
Usage: scripts/smoke-test-npm.sh [--version <version>] [--classifier <platform>]

Packs the dependency-free npm launcher, installs it into an isolated npm prefix,
then uses a local checksum-verified bundled release to exercise `jaipilot --version`.
EOF
}

die() {
  echo "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

compute_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
    return
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
    return
  fi
  if command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "$1" | awk '{print $NF}'
    return
  fi
  die "Required checksum tool not found: sha256sum, shasum, or openssl"
}

resolve_os() {
  case "$(uname -s)" in
    Linux) printf 'linux\n' ;;
    Darwin) printf 'macos\n' ;;
    *) die "Unsupported operating system: $(uname -s)" ;;
  esac
}

resolve_arch() {
  case "$(uname -m)" in
    x86_64|amd64) printf 'x64\n' ;;
    arm64|aarch64) printf 'aarch64\n' ;;
    *) die "Unsupported architecture: $(uname -m)" ;;
  esac
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || die "Missing value for --version"
      VERSION=${2#v}
      shift 2
      ;;
    --classifier)
      [ "$#" -ge 2 ] || die "Missing value for --classifier"
      CLASSIFIER=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

require_command node
require_command npm

[ -n "$VERSION" ] || VERSION=$(node -p "require('$REPO_ROOT/package.json').version")
[ -n "$CLASSIFIER" ] || CLASSIFIER="$(resolve_os)-$(resolve_arch)"

ARCHIVE="$DIST_DIR/jaipilot-$VERSION-$CLASSIFIER.tar.gz"
[ -f "$ARCHIVE" ] || die "Missing bundled release archive: $ARCHIVE"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR/package" "$SMOKE_DIR/prefix" "$SMOKE_DIR/app"

CHECKSUM="$SMOKE_DIR/jaipilot.tar.gz.sha256"
printf '%s  %s\n' "$(compute_sha256 "$ARCHIVE")" "$(basename "$ARCHIVE")" > "$CHECKSUM"

(
  cd "$REPO_ROOT"
  npm pack --pack-destination "$SMOKE_DIR/package" >/dev/null
)
PACKAGE=$(ls -1 "$SMOKE_DIR/package"/jaipilot-*.tgz | head -n 1)
[ -f "$PACKAGE" ] || die "npm pack did not create the JAIPilot package"

npm install --global --prefix "$SMOKE_DIR/prefix" "$PACKAGE" \
  --ignore-scripts --no-audit --no-fund >/dev/null

LAUNCHER="$SMOKE_DIR/prefix/bin/jaipilot"
[ -x "$LAUNCHER" ] || die "npm did not create the jaipilot bin launcher"

JAIPILOT_NPM_HOME="$SMOKE_DIR/app" \
JAIPILOT_NPM_ARCHIVE_URL="file://$ARCHIVE" \
JAIPILOT_NPM_CHECKSUM_URL="file://$CHECKSUM" \
  "$LAUNCHER" --version > "$SMOKE_DIR/version.txt"

grep -Fq "Installed JAIPilot" "$SMOKE_DIR/version.txt" \
  || die "npm launcher did not print the verified installation receipt"
tail -n 1 "$SMOKE_DIR/version.txt" > "$SMOKE_DIR/first-version.txt"
grep -Fxq "$VERSION" "$SMOKE_DIR/first-version.txt" \
  || die "npm launcher returned the wrong version: $(cat "$SMOKE_DIR/first-version.txt")"
[ -x "$SMOKE_DIR/app/current/bin/jaipilot" ] \
  || die "npm launcher did not install the bundled JAIPilot executable"
[ -x "$SMOKE_DIR/app/current/runtime/bin/java" ] \
  || die "npm launcher did not install the bundled Java runtime"

JAIPILOT_NPM_HOME="$SMOKE_DIR/app" "$LAUNCHER" --version > "$SMOKE_DIR/cached-version.txt"
cmp "$SMOKE_DIR/first-version.txt" "$SMOKE_DIR/cached-version.txt" \
  || die "cached npm launch did not preserve the installed version"

echo "Smoke-tested npm installation"
echo "  Package: $PACKAGE"
echo "  Archive: $ARCHIVE"
echo "  Launcher: $LAUNCHER"
