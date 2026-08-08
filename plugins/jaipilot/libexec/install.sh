#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG

REPO="JAIPilot/jaipilot"
APP_DIR=""
VERSION=""
ARTIFACT_URL=""
CHECKSUM_URL=""
LOCK_DIR=""
LOCK_HELD=0
CURRENT_LINK_TMP=""
TMP_DIR=""

usage() {
  cat <<'EOF'
Usage: plugins/jaipilot/libexec/install.sh --version <version> [options]

Installs JAIPilot's checksum-verified portable JAR in an owner-private plugin
data directory. A Java 17+ runtime must already be available to the host.

Options:
  --version <version>       Install a specific release version.
  --artifact-url <url>      Override the release JAR URL. Intended for testing.
  --checksum-url <url>      Override the checksum URL. Intended for testing.
  --app-dir <dir>           Plugin data directory.
                            Default: $XDG_DATA_HOME/jaipilot or ~/.local/share/jaipilot.
  -h, --help                Show this help text.
EOF
}

die() {
  echo "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

validate_version() {
  printf '%s' "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
    || die "Version must look like 1.0.0"
}

resolve_app_dir() {
  if [ -n "$APP_DIR" ]; then
    printf '%s\n' "$APP_DIR"
  elif [ -n "${XDG_DATA_HOME:-}" ]; then
    printf '%s/jaipilot\n' "$XDG_DATA_HOME"
  else
    printf '%s/.local/share/jaipilot\n' "$HOME"
  fi
}

compute_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print tolower($1)}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print tolower($1)}'
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "$1" | awk '{print tolower($NF)}'
  else
    die "Required checksum tool not found: sha256sum, shasum, or openssl"
  fi
}

read_expected_sha256() {
  expected=$(awk 'NF {print tolower($1); exit}' "$1")
  printf '%s' "$expected" | grep -Eq '^[0-9a-f]{64}$' \
    || die "Checksum file did not contain a SHA-256 digest: $1"
  printf '%s\n' "$expected"
}

download() {
  source_url=$1
  destination=$2
  retry_all=""
  if curl --help all 2>/dev/null | grep -q -- '--retry-all-errors'; then
    retry_all="--retry-all-errors"
  fi
  # GitHub release assets occasionally reset TLS connections. Keep retries
  # bounded, retry transport errors when supported, and never wait forever.
  curl -fsSL \
    --connect-timeout 10 \
    --max-time 180 \
    --retry 4 \
    --retry-delay 1 \
    --retry-connrefused \
    $retry_all \
    "$source_url" -o "$destination"
}

release_install_lock() {
  [ "$LOCK_HELD" -eq 1 ] || return
  lock_owner=""
  [ ! -f "$LOCK_DIR/pid" ] || lock_owner=$(cat "$LOCK_DIR/pid")
  if [ -z "$lock_owner" ] || [ "$lock_owner" = "$$" ]; then
    rm -f "$LOCK_DIR/pid"
    rmdir "$LOCK_DIR" 2>/dev/null || true
  fi
  LOCK_HELD=0
}

cleanup() {
  [ -z "$CURRENT_LINK_TMP" ] || rm -f "$CURRENT_LINK_TMP"
  release_install_lock
  [ -z "$TMP_DIR" ] || rm -rf "$TMP_DIR"
}

acquire_install_lock() {
  LOCK_DIR="$APP_DIR/.install-lock"
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    owner_pid=""
    [ ! -f "$LOCK_DIR/pid" ] || owner_pid=$(cat "$LOCK_DIR/pid")
    case "$owner_pid" in
      ''|*[!0-9]*) die "Another JAIPilot install is using $APP_DIR" ;;
      *)
        if kill -0 "$owner_pid" 2>/dev/null; then
          die "Another JAIPilot install is using $APP_DIR (PID $owner_pid)"
        fi
        ;;
    esac
    rm -f "$LOCK_DIR/pid"
    rmdir "$LOCK_DIR" 2>/dev/null || die "Could not clear stale install lock: $LOCK_DIR"
    mkdir "$LOCK_DIR" 2>/dev/null || die "Another JAIPilot install started for $APP_DIR"
  fi
  LOCK_HELD=1
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || die "Missing value for --version"
      VERSION=${2#v}
      shift 2
      ;;
    --artifact-url)
      [ "$#" -ge 2 ] || die "Missing value for --artifact-url"
      ARTIFACT_URL=$2
      shift 2
      ;;
    --checksum-url)
      [ "$#" -ge 2 ] || die "Missing value for --checksum-url"
      CHECKSUM_URL=$2
      shift 2
      ;;
    --app-dir)
      [ "$#" -ge 2 ] || die "Missing value for --app-dir"
      APP_DIR=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *) die "Unknown option: $1" ;;
  esac
done

[ -n "$VERSION" ] || die "--version is required"
validate_version "$VERSION"
APP_DIR=$(resolve_app_dir)

if [ -z "$ARTIFACT_URL" ]; then
  ARTIFACT_URL="https://github.com/$REPO/releases/download/v$VERSION/jaipilot-toolkit-$VERSION.jar"
fi
if [ -z "$CHECKSUM_URL" ]; then
  CHECKSUM_URL="$ARTIFACT_URL.sha256"
fi

require_command curl
require_command grep
require_command mktemp

umask 077
mkdir -p "$APP_DIR"
chmod 700 "$APP_DIR" 2>/dev/null || true
trap cleanup EXIT HUP INT TERM
acquire_install_lock
TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/jaipilot-install.XXXXXX")

if [ -e "$APP_DIR/current" ] && [ ! -L "$APP_DIR/current" ]; then
  die "Current release path is not a symlink: $APP_DIR/current"
fi

ARTIFACT_PATH="$TMP_DIR/jaipilot-toolkit.jar"
CHECKSUM_PATH="$TMP_DIR/jaipilot-toolkit.jar.sha256"
download "$ARTIFACT_URL" "$ARTIFACT_PATH"
download "$CHECKSUM_URL" "$CHECKSUM_PATH"

EXPECTED_SHA256=$(read_expected_sha256 "$CHECKSUM_PATH")
ACTUAL_SHA256=$(compute_sha256 "$ARTIFACT_PATH")
[ "$EXPECTED_SHA256" = "$ACTUAL_SHA256" ] \
  || die "SHA-256 mismatch for downloaded JAIPilot payload."

VERSIONS_DIR="$APP_DIR/versions"
VERSION_DIR="$VERSIONS_DIR/$VERSION"
mkdir -p "$VERSIONS_DIR"
[ ! -L "$VERSION_DIR" ] || die "Version path must not be a symbolic link: $VERSION_DIR"
if [ -e "$VERSION_DIR" ] && [ ! -d "$VERSION_DIR" ]; then
  die "Version path is not a directory: $VERSION_DIR"
fi
mkdir -p "$VERSION_DIR"
chmod 700 "$VERSION_DIR" 2>/dev/null || true

INSTALLED_JAR="$VERSION_DIR/jaipilot-toolkit.jar"
INSTALLED_TMP="$VERSION_DIR/.jaipilot-toolkit.jar.$$"
cp "$ARTIFACT_PATH" "$INSTALLED_TMP"
chmod 600 "$INSTALLED_TMP"
mv -f "$INSTALLED_TMP" "$INSTALLED_JAR"
printf '%s\n' "$ACTUAL_SHA256" > "$VERSION_DIR/jaipilot-toolkit.jar.sha256"
chmod 600 "$VERSION_DIR/jaipilot-toolkit.jar.sha256"

CURRENT_LINK_TMP="$APP_DIR/.current.$$"
rm -f "$CURRENT_LINK_TMP"
ln -s "versions/$VERSION" "$CURRENT_LINK_TMP"
rm -f "$APP_DIR/current"
mv "$CURRENT_LINK_TMP" "$APP_DIR/current"
CURRENT_LINK_TMP=""

echo "Installed JAIPilot $VERSION plugin payload" >&2
echo "  Artifact: $ARTIFACT_URL" >&2
echo "  SHA-256: $ACTUAL_SHA256" >&2
echo "  Payload: $INSTALLED_JAR" >&2
