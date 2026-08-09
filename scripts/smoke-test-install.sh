#!/usr/bin/env sh
set -eu

LC_ALL=C
LANG=C
export LC_ALL LANG

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
INSTALLER="$REPO_ROOT/plugins/jaipilot/libexec/install.sh"
PLUGIN_ROOT="$REPO_ROOT/plugins/jaipilot"
PLUGIN_RUNNER="$PLUGIN_ROOT/bin/jaipilot"
DIST_DIR="$REPO_ROOT/target/distributions"
SMOKE_DIR="$REPO_ROOT/target/smoke-install"
VERSION=""
RETRY_SERVER_PID=""

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
    die "Required checksum tool not found"
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
      echo "Usage: scripts/smoke-test-install.sh [--version <version>]"
      exit 0
      ;;
    *) die "Unknown option: $1" ;;
  esac
done

if [ -n "$VERSION" ]; then
  ARTIFACT="$DIST_DIR/jaipilot-toolkit-$VERSION.jar"
else
  ARTIFACT=$(ls -1t "$DIST_DIR"/jaipilot-toolkit-*.jar 2>/dev/null | head -n 1)
  VERSION=$(basename "$ARTIFACT" | sed -n 's/^jaipilot-toolkit-\([0-9][0-9.]*\)\.jar$/\1/p')
fi
[ -n "${ARTIFACT:-}" ] && [ -f "$ARTIFACT" ] || die "Missing portable JAIPilot JAR"
[ -n "$VERSION" ] || die "Could not determine the plugin payload version"
CHECKSUM_FILE="$ARTIFACT.sha256"
printf '%s  %s\n' "$(compute_sha256 "$ARTIFACT")" "$(basename "$ARTIFACT")" > "$CHECKSUM_FILE"

ARTIFACT_BYTES=$(wc -c < "$ARTIFACT" | tr -d ' ')
[ "$ARTIFACT_BYTES" -lt 20971520 ] || die "Plugin payload exceeds the 20 MiB release cap"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR"
STATE_HOME="$SMOKE_DIR/state"
JAVA_PROJECT="$SMOKE_DIR/java-project"
mkdir -p "$JAVA_PROJECT/src/main/java/example"
printf '<project/>\n' > "$JAVA_PROJECT/pom.xml"
printf 'package example; public class Sample {}\n' \
  > "$JAVA_PROJECT/src/main/java/example/Sample.java"
git -C "$JAVA_PROJECT" init -q

cleanup() {
  if [ -n "$RETRY_SERVER_PID" ]; then
    kill "$RETRY_SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT HUP INT TERM

if JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/incomplete-app" \
  JAIPILOT_BOOTSTRAP_ARTIFACT_URL="file://$ARTIFACT" \
  "$PLUGIN_RUNNER" version > "$SMOKE_DIR/incomplete.log" 2>&1; then
  die "Plugin bootstrap accepted an artifact override without its checksum"
fi
grep -Fq "artifact and checksum overrides must be set together" "$SMOKE_DIR/incomplete.log" \
  || die "Plugin bootstrap did not explain the incomplete override"

JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/app" \
JAIPILOT_BOOTSTRAP_ARTIFACT_URL="file://$ARTIFACT" \
JAIPILOT_BOOTSTRAP_CHECKSUM_URL="file://$CHECKSUM_FILE" \
  "$PLUGIN_RUNNER" version > "$SMOKE_DIR/version.json"
grep -Fq "\"version\" : \"$VERSION\"" "$SMOKE_DIR/version.json" \
  || die "Installed plugin payload did not report version $VERSION"
[ -f "$SMOKE_DIR/app/versions/$VERSION/jaipilot-toolkit.jar" ] \
  || die "Installer did not persist the portable JAR"
[ -L "$SMOKE_DIR/app/current" ] || die "Installer did not publish the current version link"
[ ! -d "$SMOKE_DIR/app/current/runtime" ] || die "Installer unexpectedly bundled a private JRE"
[ ! -d "$SMOKE_DIR/app/current/plugins" ] || die "Installer duplicated the host plugin"

JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/app" "$PLUGIN_RUNNER" version \
  > "$SMOKE_DIR/cached-version.json"
grep -Fq "\"version\" : \"$VERSION\"" "$SMOKE_DIR/cached-version.json" \
  || die "Plugin did not reuse its cached payload"

# A transient HTTP transport failure must be retried without weakening checksum verification.
cat > "$SMOKE_DIR/retry-server.py" <<'PY'
import http.server
import pathlib
import sys

artifact, checksum, counter, port_file = map(pathlib.Path, sys.argv[1:])

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        count = int(counter.read_text() if counter.exists() else "0") + 1
        counter.write_text(str(count))
        if count <= 2:
            self.send_response(503)
            self.end_headers()
            return
        source = artifact if self.path == "/payload.jar" else checksum
        payload = source.read_bytes()
        self.send_response(200)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_args):
        return

server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
port_file.write_text(str(server.server_port))
server.serve_forever()
PY
python3 "$SMOKE_DIR/retry-server.py" \
  "$ARTIFACT" "$CHECKSUM_FILE" "$SMOKE_DIR/curl-count" "$SMOKE_DIR/retry-port" &
RETRY_SERVER_PID=$!
attempt=0
while [ "$attempt" -lt 50 ] && [ ! -f "$SMOKE_DIR/retry-port" ]; do
  attempt=$((attempt + 1))
  sleep 0.1
done
[ -f "$SMOKE_DIR/retry-port" ] || die "Retry fixture server did not start"
RETRY_PORT=$(cat "$SMOKE_DIR/retry-port")
"$INSTALLER" \
  --version "$VERSION" \
  --artifact-url "http://127.0.0.1:$RETRY_PORT/payload.jar" \
  --checksum-url "http://127.0.0.1:$RETRY_PORT/payload.jar.sha256" \
  --app-dir "$SMOKE_DIR/retry-app" \
  > "$SMOKE_DIR/retry.log" 2>&1
kill "$RETRY_SERVER_PID" 2>/dev/null || true
RETRY_SERVER_PID=""
[ "$(cat "$SMOKE_DIR/curl-count")" -ge 4 ] \
  || die "Installer did not retry a transient curl transport failure"

mkdir -p "$SMOKE_DIR/lock-app/.install-lock"
printf '%s\n' "$$" > "$SMOKE_DIR/lock-app/.install-lock/pid"
if "$INSTALLER" --version "$VERSION" \
  --artifact-url "file://$ARTIFACT" \
  --checksum-url "file://$CHECKSUM_FILE" \
  --app-dir "$SMOKE_DIR/lock-app" > "$SMOKE_DIR/lock.log" 2>&1; then
  die "Installer ignored an active install lock"
fi
grep -Fq "Another JAIPilot install is using" "$SMOKE_DIR/lock.log" \
  || die "Installer did not report its active lock"

# The plugin is available to the agent but performs no repository work merely
# because the host starts its MCP server.
[ ! -e "$PLUGIN_ROOT/hooks" ] || die "Plugin still contains automatic coding-tool hooks"
(
  cd "$PLUGIN_ROOT"
  JAIPILOT_STATE_HOME="$STATE_HOME" \
  JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/app" \
  JAIPILOT_DASHBOARD_DISABLED=1 \
    python3 "$REPO_ROOT/scripts/smoke-test-mcp.py" ./bin/jaipilot-mcp
)
[ ! -e "$STATE_HOME/repositories" ] \
  || die "MCP initialization analyzed or registered a repository without an agent tool call"

# An explicit agent-selected snapshot initializes the common dashboard store.
JAIPILOT_STATE_HOME="$STATE_HOME" \
JAIPILOT_RUNTIME_HOME="$SMOKE_DIR/app" \
JAIPILOT_DASHBOARD_DISABLED=1 \
  "$PLUGIN_RUNNER" snapshot --project "$JAVA_PROJECT" > "$SMOKE_DIR/snapshot.json"
snapshot=$(find "$STATE_HOME/repositories" -type f -name '*.json' -print 2>/dev/null | head -n 1 || true)
[ -n "${snapshot:-}" ] || die "Explicit snapshot did not register the Java repository"
grep -Fq '"analysisStatus" : "ready"' "$snapshot" \
  || die "Explicit snapshot did not initialize Java quality"

echo "Smoke-tested lean plugin installation"
echo "  Artifact: $ARTIFACT"
echo "  Bytes: $ARTIFACT_BYTES"
echo "  Runtime: host Java 17+"
