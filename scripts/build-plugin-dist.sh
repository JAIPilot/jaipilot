#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
TARGET_DIR="$REPO_ROOT/target"
DIST_DIR="$TARGET_DIR/distributions"
VERSION=""

die() {
  echo "$1" >&2
  exit 1
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

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || die "Missing value for --version"
      VERSION=${2#v}
      shift 2
      ;;
    -h|--help)
      echo "Usage: scripts/build-plugin-dist.sh --version <version>"
      exit 0
      ;;
    *) die "Unknown option: $1" ;;
  esac
done

printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || die "Version must look like 1.0.0"

SOURCE_JAR="$TARGET_DIR/jaipilot-toolkit-$VERSION-all.jar"
ARTIFACT="$DIST_DIR/jaipilot-toolkit-$VERSION.jar"
CHECKSUM="$ARTIFACT.sha256"
[ -f "$SOURCE_JAR" ] || die "Missing shaded jar: $SOURCE_JAR. Run ./mvnw package first."

mkdir -p "$DIST_DIR"
cp "$SOURCE_JAR" "$ARTIFACT"
printf '%s  %s\n' "$(compute_sha256 "$ARTIFACT")" "$(basename "$ARTIFACT")" > "$CHECKSUM"

echo "Built portable plugin payload"
echo "  Artifact: $ARTIFACT"
echo "  SHA-256: $(awk '{print $1}' "$CHECKSUM")"
