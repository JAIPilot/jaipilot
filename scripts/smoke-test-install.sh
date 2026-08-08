#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
INSTALLER="$REPO_ROOT/plugins/jaipilot/libexec/install.sh"
PLUGIN_RUNNER="$REPO_ROOT/plugins/jaipilot/bin/jaipilot"
DIST_DIR="$REPO_ROOT/target/distributions"
SMOKE_DIR="$REPO_ROOT/target/smoke-install"
VERSION=""
CLASSIFIER=""

usage() {
  cat <<'EOF'
Usage: scripts/smoke-test-install.sh [--version <version>] [--classifier <platform>]

Smoke-tests the plugin runner installation against a local release archive.
EOF
}

die() {
  echo "$1" >&2
  exit 1
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

resolve_classifier() {
  if [ -n "$CLASSIFIER" ]; then
    printf '%s\n' "$CLASSIFIER"
    return
  fi
  printf '%s-%s\n' "$(resolve_os)" "$(resolve_arch)"
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

[ -d "$DIST_DIR" ] || die "Missing distribution directory: $DIST_DIR"
RESOLVED_CLASSIFIER=$(resolve_classifier)

if [ -n "$VERSION" ]; then
  TAR_GZ="$DIST_DIR/jaipilot-toolkit-$VERSION-$RESOLVED_CLASSIFIER.tar.gz"
else
  TAR_GZ=$(ls -1t "$DIST_DIR"/jaipilot-toolkit-*-"$RESOLVED_CLASSIFIER".tar.gz 2>/dev/null | head -n 1)
fi

[ -n "${TAR_GZ:-}" ] || die "Could not find a JAIPilot tar.gz distribution under $DIST_DIR"
[ -f "$TAR_GZ" ] || die "Missing distribution archive: $TAR_GZ"
CHECKSUM_FILE="$TAR_GZ.sha256"
INSTALL_VERSION=${VERSION:-$(basename "$TAR_GZ" | sed -n "s/^jaipilot-toolkit-\\([0-9][0-9.]*\\)-$RESOLVED_CLASSIFIER\\.tar\\.gz$/\\1/p")}
[ -n "${INSTALL_VERSION:-}" ] || die "Could not determine the distribution version from $TAR_GZ"

printf '%s  %s\n' "$(compute_sha256 "$TAR_GZ")" "$(basename "$TAR_GZ")" > "$CHECKSUM_FILE"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR"
JAIPILOT_STATE_HOME="$SMOKE_DIR/state"
export JAIPILOT_STATE_HOME

cleanup_dashboard() {
  metadata="$JAIPILOT_STATE_HOME/dashboard/server.json"
  [ -f "$metadata" ] || return
  dashboard_pid=$(sed -n 's/.*"pid" : \([0-9][0-9]*\).*/\1/p' "$metadata" | head -n 1)
  case "$dashboard_pid" in
    ''|*[!0-9]*) return ;;
    *) kill "$dashboard_pid" 2>/dev/null || true ;;
  esac
}
trap cleanup_dashboard EXIT HUP INT TERM

if JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/incomplete-app" \
  JAIPILOT_BOOTSTRAP_ARCHIVE_URL="file://$TAR_GZ" \
  "$PLUGIN_RUNNER" version > "$SMOKE_DIR/incomplete-bootstrap.log" 2>&1; then
  die "Plugin bootstrap accepted an archive override without its checksum"
fi
grep -Fq "archive and checksum overrides must be set together" \
  "$SMOKE_DIR/incomplete-bootstrap.log" \
  || die "Plugin bootstrap did not report incomplete archive overrides cleanly"

JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/plugin-app" \
JAIPILOT_BOOTSTRAP_ARCHIVE_URL="file://$TAR_GZ" \
JAIPILOT_BOOTSTRAP_CHECKSUM_URL="file://$CHECKSUM_FILE" \
  "$PLUGIN_RUNNER" version > "$SMOKE_DIR/plugin-version.json"
grep -Fq "\"version\" : \"$INSTALL_VERSION\"" "$SMOKE_DIR/plugin-version.json" \
  || die "Plugin bootstrap did not install and run version $INSTALL_VERSION"
JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/plugin-app" \
  "$PLUGIN_RUNNER" dashboard > "$SMOKE_DIR/plugin-dashboard.json"
grep -Fq '"running" : true' "$SMOKE_DIR/plugin-dashboard.json" \
  || die "Plugin bootstrap did not start the local dashboard"
DASHBOARD_URL=$(awk -F '"' '/"url"/ {print $4; exit}' "$SMOKE_DIR/plugin-dashboard.json")
case "$DASHBOARD_URL" in
  http://127.0.0.1:*/) ;;
  *) die "Plugin dashboard did not report a loopback URL" ;;
esac
curl -fsS "${DASHBOARD_URL}api/health" > "$SMOKE_DIR/plugin-dashboard-health.json"
grep -Fq '"service" : "jaipilot-dashboard"' "$SMOKE_DIR/plugin-dashboard-health.json" \
  || die "Plugin dashboard health endpoint was unavailable"
JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/plugin-app" \
  "$PLUGIN_RUNNER" version > "$SMOKE_DIR/plugin-cached-version.json"
grep -Fq "\"version\" : \"$INSTALL_VERSION\"" "$SMOKE_DIR/plugin-cached-version.json" \
  || die "Plugin bootstrap did not reuse the cached version"

for removed_option in --prefix --bin-dir --lib-dir --no-bin-link; do
  option_name=${removed_option#--}
  if "$INSTALLER" "$removed_option" retired \
    > "$SMOKE_DIR/removed-$option_name.log" 2>&1; then
    die "Installer still accepted removed option $removed_option"
  fi
  grep -Fq "Unknown option: $removed_option" "$SMOKE_DIR/removed-$option_name.log" \
    || die "Installer did not reject removed option $removed_option cleanly"
done

mkdir -p "$SMOKE_DIR/security-app/victim"
printf 'keep\n' > "$SMOKE_DIR/security-app/victim/marker"
MALICIOUS_VERSION=$(printf '1.0.0\n../../../victim')
if "$INSTALLER" \
  --version "$MALICIOUS_VERSION" \
  --archive-url "file://$TAR_GZ" \
  --checksum-url "file://$CHECKSUM_FILE" \
  --app-dir "$SMOKE_DIR/security-app" \
  > "$SMOKE_DIR/invalid-version.log" 2>&1; then
  die "Installer accepted a path-traversal version"
fi
grep -Fq "Version must look like 1.0.0" "$SMOKE_DIR/invalid-version.log" \
  || die "Installer did not report an invalid version cleanly"
[ -f "$SMOKE_DIR/security-app/victim/marker" ] || die "Invalid version escaped the versions directory"

mkdir -p "$SMOKE_DIR/lock-app/.install-lock"
printf '%s\n' "$$" > "$SMOKE_DIR/lock-app/.install-lock/pid"
if "$INSTALLER" \
  --version "$INSTALL_VERSION" \
  --archive-url "file://$TAR_GZ" \
  --checksum-url "file://$CHECKSUM_FILE" \
  --app-dir "$SMOKE_DIR/lock-app" \
  > "$SMOKE_DIR/active-lock.log" 2>&1; then
  die "Installer ignored an active install lock"
fi
grep -Fq "Another JAIPilot install is using" "$SMOKE_DIR/active-lock.log" \
  || die "Installer did not report the active install lock cleanly"
[ "$(cat "$SMOKE_DIR/lock-app/.install-lock/pid")" = "$$" ] \
  || die "Installer changed another process's active lock"

"$INSTALLER" \
  --version "$INSTALL_VERSION" \
  --archive-url "file://$TAR_GZ" \
  --checksum-url "file://$CHECKSUM_FILE" \
  --app-dir "$SMOKE_DIR/app"

[ -L "$SMOKE_DIR/app/current" ] || die "Install did not create the current symlink"
[ -x "$SMOKE_DIR/app/bin/jaipilot" ] || die "Install did not create the stable toolkit-harness launcher"
[ -x "$SMOKE_DIR/app/current/libexec/install.sh" ] || die "Distribution did not include the self-update installer"
[ -x "$SMOKE_DIR/app/current/plugins/jaipilot/hooks/post-tool-use.sh" ] \
  || die "Distribution did not include the executable post-tool hook"
"$SMOKE_DIR/app/bin/jaipilot" version > "$SMOKE_DIR/version.json"
grep -Fq "\"version\" : \"$INSTALL_VERSION\"" "$SMOKE_DIR/version.json" \
  || die "Installed toolkit harness did not report version $INSTALL_VERSION"

printf '%s' '{"hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{"command":"git status --short"}}' \
  | PLUGIN_ROOT="$SMOKE_DIR/app/current/plugins/jaipilot" \
    JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/app" \
    JAIPILOT_DASHBOARD_DISABLED=1 \
    "$SMOKE_DIR/app/current/plugins/jaipilot/hooks/post-tool-use.sh" \
    > "$SMOKE_DIR/non-commit-hook.json"
[ ! -s "$SMOKE_DIR/non-commit-hook.json" ] || die "Post-tool hook emitted output for a non-commit command"

printf '%s' '{"hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{"command":"git commit -m smoke"}}' \
  | PLUGIN_ROOT="$SMOKE_DIR/app/current/plugins/jaipilot" \
    JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/app" \
    JAIPILOT_DASHBOARD_DISABLED=1 \
    "$SMOKE_DIR/app/current/plugins/jaipilot/hooks/post-tool-use.sh" \
    > "$SMOKE_DIR/post-commit-hook.json"
grep -Fq '"source" : "Automatic post-commit analysis"' \
  "$SMOKE_DIR/state/metrics/summary.json" \
  || die "Installed post-tool hook did not persist current project quality"
grep -Fq '"revision"' "$SMOKE_DIR/state/metrics/summary.json" \
  || die "Installed post-tool hook did not persist the analyzed Git revision"

STABLE_RUNNER_SHA256=$(compute_sha256 "$SMOKE_DIR/app/bin/jaipilot")
"$SMOKE_DIR/app/current/libexec/install.sh" \
  --version "$INSTALL_VERSION" \
  --archive-url "file://$TAR_GZ" \
  --checksum-url "file://$CHECKSUM_FILE" \
  --app-dir "$SMOKE_DIR/app"

[ "$STABLE_RUNNER_SHA256" = "$(compute_sha256 "$SMOKE_DIR/app/bin/jaipilot")" ] \
  || die "Self-update changed the stable plugin runner unexpectedly"
[ -L "$SMOKE_DIR/app/current" ] || die "Self-update did not preserve the current symlink"
[ -x "$SMOKE_DIR/app/bin/jaipilot" ] || die "Self-update did not preserve the toolkit-harness launcher"
"$SMOKE_DIR/app/bin/jaipilot" version > "$SMOKE_DIR/app-version.json"

rm -f "$SMOKE_DIR/app/current"
mkdir -p "$SMOKE_DIR/app/current"
if "$INSTALLER" \
  --version "$INSTALL_VERSION" \
  --archive-url "file://$TAR_GZ" \
  --checksum-url "file://$CHECKSUM_FILE" \
  --app-dir "$SMOKE_DIR/app" \
  > "$SMOKE_DIR/invalid-current.log" 2>&1; then
  die "Installer replaced a non-symlink current directory"
fi
grep -Fq "Current release path is not a symlink" "$SMOKE_DIR/invalid-current.log" \
  || die "Installer did not report the unsafe current path cleanly"
[ -d "$SMOKE_DIR/app/current" ] && [ ! -L "$SMOKE_DIR/app/current" ] \
  || die "Installer changed the non-symlink current directory"
[ ! -d "$SMOKE_DIR/app/.install-lock" ] || die "Installer left its lock after a failed update"

echo "Smoke-tested install script"
echo "  Archive: $TAR_GZ"
