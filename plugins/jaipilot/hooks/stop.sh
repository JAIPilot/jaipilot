#!/usr/bin/env sh
set -eu

plugin_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
project_root=$("$plugin_root/hooks/java-project-root.sh" .) || exit 0
[ -x "$plugin_root/bin/jaipilot" ] || exit 0
# SessionStart owns bootstrap in a detached worker. Stop must never block or
# fail merely because that background download is incomplete or unavailable.
JAIPILOT_BOOTSTRAP_DISABLED=1
export JAIPILOT_BOOTSTRAP_DISABLED
exec "$plugin_root/bin/jaipilot" hook-stop --project "$project_root"
