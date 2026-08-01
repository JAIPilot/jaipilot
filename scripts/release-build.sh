#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG
# ./scripts/release-build.sh --version 1.0.0 --push
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
POM_FILE="$REPO_ROOT/pom.xml"
PACKAGE_FILE="$REPO_ROOT/package.json"
PLUGIN_FILES="$REPO_ROOT/plugin/jaipilot/.codex-plugin/plugin.json $REPO_ROOT/plugin/jaipilot/plugin.json $REPO_ROOT/plugin/jaipilot/.claude-plugin/plugin.json"
VERSION=""
PUSH_CHANGES=0

usage() {
  cat <<'EOF'
Usage: scripts/release-build.sh --version <version> [--push]

Prepares a new JAIPilot release by:
  1. Updating the Maven, npm, and cross-agent plugin versions.
  2. Running the full Maven and npm verification gates.
  3. Smoke-testing shell and npm installation for that version.
  4. Creating a release commit (including current worktree changes) and annotated git tag.

Options:
  --version <version>  Release version such as 1.0.0.
  --push               Push main and the release tag to origin after tagging.
  -h, --help           Show this help text.
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
  printf '%s' "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || die "Version must look like 1.0.0"
}

current_version() {
  perl -ne 'print "$1\n" if /<revision>([^<]+)<\/revision>/' "$POM_FILE" | head -n 1
}

current_branch() {
  git branch --show-current
}

update_versions() {
  NEW_VERSION=$1 perl -0pi -e 's#<revision>[^<]+</revision>#<revision>$ENV{NEW_VERSION}</revision>#' "$POM_FILE"
  for plugin_file in $PLUGIN_FILES; do
    NEW_VERSION=$1 perl -0pi -e 's#"version"\s*:\s*"[^"]+"#"version": "$ENV{NEW_VERSION}"#' "$plugin_file"
  done
  (
    cd "$REPO_ROOT"
    npm version "$1" --no-git-tag-version --allow-same-version --ignore-scripts >/dev/null
  )
}

ensure_version_applied() {
  expected=$1
  [ "$(current_version)" = "$expected" ] || die "Failed to update pom.xml to version $expected"
  [ "$(node -p "require('$PACKAGE_FILE').version")" = "$expected" ] \
    || die "Failed to update package.json to version $expected"
  for plugin_file in $PLUGIN_FILES; do
    grep -Fq "\"version\": \"$expected\"" "$plugin_file" \
      || die "Failed to update plugin version in $plugin_file"
  done
}

ensure_tag_absent() {
  tag_name=$1
  git rev-parse -q --verify "refs/tags/$tag_name" >/dev/null 2>&1 && die "Tag already exists locally: $tag_name"
  if [ "$PUSH_CHANGES" -eq 1 ]; then
    if remote_refs=$(git ls-remote --tags origin "refs/tags/$tag_name" 2>/dev/null); then
      if [ -n "$remote_refs" ]; then
        die "Tag already exists on origin: $tag_name"
      fi
    else
      die "Unable to query tags from origin. Verify remote access before releasing."
    fi
  fi
}

commit_and_tag() {
  version=$1
  git add -A
  git diff --cached --quiet --ignore-submodules -- && die "No changes to commit for release $version."
  git commit -m "Release $version"
  git tag -a "v$version" -m "Release $version"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || die "Missing value for --version"
      VERSION=${2#v}
      shift 2
      ;;
    --push)
      PUSH_CHANGES=1
      shift
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

[ -n "$VERSION" ] || die "--version is required"

require_command git
require_command perl
require_command grep
require_command node
require_command npm

validate_version "$VERSION"

cd "$REPO_ROOT"
[ "$(current_branch)" = "main" ] || die "Release script must be run from the main branch."

CURRENT_VERSION=$(current_version)
[ -n "$CURRENT_VERSION" ] || die "Could not determine the current project version."

ensure_tag_absent "v$VERSION"

if [ "$CURRENT_VERSION" != "$VERSION" ] \
  || [ "$(node -p "require('$PACKAGE_FILE').version")" != "$VERSION" ]; then
  update_versions "$VERSION"
  ensure_version_applied "$VERSION"
fi

./mvnw -B clean verify
npm ci --ignore-scripts
./scripts/smoke-test-install.sh --version "$VERSION"
./scripts/smoke-test-npm.sh --version "$VERSION"

commit_and_tag "$VERSION"

if [ "$PUSH_CHANGES" -eq 1 ]; then
  git push origin main "v$VERSION"
  echo "Released JAIPilot $VERSION"
  echo "  Commit: $(git rev-parse --short HEAD)"
  echo "  Tag: v$VERSION"
  echo "  Pushed: origin/main and origin/v$VERSION"
else
  echo "Prepared JAIPilot $VERSION"
  echo "  Commit: $(git rev-parse --short HEAD)"
  echo "  Tag: v$VERSION"
  echo "Next:"
  echo "  git push origin main v$VERSION"
fi
