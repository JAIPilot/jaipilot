#!/usr/bin/env sh
set -eu

plugin_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
project_root=$("$plugin_root/hooks/java-project-root.sh" .) || exit 0
exec "$plugin_root/bin/jaipilot" hook-stop --project "$project_root"
