#!/usr/bin/env sh
set -eu

hook_input=$(cat)
case "$hook_input" in
  *git*commit*|*Git*commit*) ;;
  *) exit 0 ;;
esac

plugin_root=${PLUGIN_ROOT:-${CLAUDE_PLUGIN_ROOT:-}}
if [ -z "$plugin_root" ]; then
  exit 0
fi

# Reuse the detached, repository-safe snapshot path. The Stop hook supplies any
# proof feedback after this best-effort current-state refresh completes.
exec "$plugin_root/hooks/session-start.sh" </dev/null
