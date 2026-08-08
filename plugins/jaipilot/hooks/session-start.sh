#!/usr/bin/env sh
set -eu

PLUGIN_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ "${1:-}" = "--worker" ]; then
  [ "$#" -eq 2 ] || exit 0
  PROJECT_ROOT=$("$PLUGIN_ROOT/hooks/java-project-root.sh" "$2") || exit 0
  "$PLUGIN_ROOT/bin/jaipilot" snapshot --project "$PROJECT_ROOT" >/dev/null || exit 0
  exec "$PLUGIN_ROOT/bin/jaipilot" dashboard >/dev/null
fi

PROJECT_ROOT=$("$PLUGIN_ROOT/hooks/java-project-root.sh" .) || exit 0

if [ -n "${JAIPILOT_STATE_HOME:-}" ]; then
  STATE_ROOT=$JAIPILOT_STATE_HOME
elif [ -n "${XDG_STATE_HOME:-}" ]; then
  STATE_ROOT="$XDG_STATE_HOME/jaipilot"
else
  STATE_ROOT="$HOME/.local/state/jaipilot"
fi

# Repository discovery never writes into the repository. The checksum-verified
# runtime bootstrap and whole-project snapshot are detached from the host hook.
umask 077
LOG_DIR="$STATE_ROOT/session"
mkdir -p "$LOG_DIR" 2>/dev/null || exit 0
chmod 700 "$LOG_DIR" 2>/dev/null || exit 0
TEMP_LOG=$(mktemp "$LOG_DIR/.session-start.XXXXXX") || exit 0
chmod 600 "$TEMP_LOG" 2>/dev/null || exit 0
LOG_PATH="$LOG_DIR/session-start.log"
mv -f "$TEMP_LOG" "$LOG_PATH" 2>/dev/null || exit 0
[ ! -L "$LOG_PATH" ] && [ -f "$LOG_PATH" ] || exit 0
nohup "$0" --worker "$PROJECT_ROOT" </dev/null >>"$LOG_PATH" 2>&1 &
exit 0
