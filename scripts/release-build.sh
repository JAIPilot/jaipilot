#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG
# ./scripts/release-build.sh --version 1.0.0 --push
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
POM_FILE="$REPO_ROOT/pom.xml"
PLUGIN_FILES="$REPO_ROOT/plugins/jaipilot/.codex-plugin/plugin.json $REPO_ROOT/plugins/jaipilot/plugin.json $REPO_ROOT/plugins/jaipilot/.claude-plugin/plugin.json"
CLAUDE_MARKETPLACE_FILE="$REPO_ROOT/.claude-plugin/marketplace.json"
PLUGIN_BOOTSTRAP_FILE="$REPO_ROOT/plugins/jaipilot/bin/jaipilot"
VERSION=""
PUSH_CHANGES=0

usage() {
  cat <<'EOF'
Usage: scripts/release-build.sh --version <version> [--push]

Prepares a new JAIPilot release by:
  1. Updating the Maven and cross-agent plugin versions.
  2. Running the full Java and plugin verification gates.
  3. Smoke-testing checksum-verified plugin installation for that version.
  4. Creating a version-only release commit and annotated git tag.

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
  NEW_VERSION=$1 perl -0pi -e 's#"version"\s*:\s*"[^"]+"#"version": "$ENV{NEW_VERSION}"#' \
    "$CLAUDE_MARKETPLACE_FILE"
  NEW_VERSION=$1 perl -0pi -e 's#VERSION="[0-9]+\.[0-9]+\.[0-9]+"#VERSION="$ENV{NEW_VERSION}"#' \
    "$PLUGIN_BOOTSTRAP_FILE"
}

ensure_version_applied() {
  expected=$1
  [ "$(current_version)" = "$expected" ] || die "Failed to update pom.xml to version $expected"
  for plugin_file in $PLUGIN_FILES; do
    grep -Fq "\"version\": \"$expected\"" "$plugin_file" \
      || die "Failed to update plugin version in $plugin_file"
  done
  grep -Fq "\"version\": \"$expected\"" "$CLAUDE_MARKETPLACE_FILE" \
    || die "Failed to update Claude marketplace version"
  grep -Fq "VERSION=\"$expected\"" "$PLUGIN_BOOTSTRAP_FILE" \
    || die "Failed to update plugin bootstrap version"
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
  git diff --cached --quiet --ignore-submodules -- && die "No changes to commit for release $version."
  git commit -m "Release $version"
  git tag -a "v$version" -m "Release $version"
}

ensure_clean_tree() {
  [ -z "$(git status --porcelain --untracked-files=all)" ] \
    || die "Release preparation requires a clean main worktree."
}

stage_release_versions() {
  git add -- \
    pom.xml \
    plugins/jaipilot/plugin.json \
    plugins/jaipilot/.codex-plugin/plugin.json \
    plugins/jaipilot/.claude-plugin/plugin.json \
    .claude-plugin/marketplace.json \
    plugins/jaipilot/bin/jaipilot
}

ensure_release_scope() {
  unexpected=$(git ls-files --others --exclude-standard)
  [ -z "$unexpected" ] || die "Release gates created unexpected untracked files: $unexpected"
  for changed in $(git diff --name-only HEAD); do
    case "$changed" in
      pom.xml|plugins/jaipilot/plugin.json|plugins/jaipilot/.codex-plugin/plugin.json|\
      plugins/jaipilot/.claude-plugin/plugin.json|.claude-plugin/marketplace.json|\
      plugins/jaipilot/bin/jaipilot) ;;
      *) die "Release gates changed an unexpected tracked path: $changed" ;;
    esac
  done
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
require_command python3

validate_version "$VERSION"

cd "$REPO_ROOT"
[ "$(current_branch)" = "main" ] || die "Release script must be run from the main branch."
ensure_clean_tree

CURRENT_VERSION=$(current_version)
[ -n "$CURRENT_VERSION" ] || die "Could not determine the current project version."

ensure_tag_absent "v$VERSION"

update_versions "$VERSION"
ensure_version_applied "$VERSION"
stage_release_versions

./mvnw -B clean verify
python3 ./scripts/validate-plugin.py
./scripts/smoke-test-install.sh --version "$VERSION"
ensure_release_scope

commit_and_tag "$VERSION"

if [ "$PUSH_CHANGES" -eq 1 ]; then
  git push --atomic origin main "v$VERSION"
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
