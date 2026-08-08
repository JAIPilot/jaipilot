#!/usr/bin/env sh
set -eu

hook_input=$(cat)
case "$hook_input" in
  *git*commit*) ;;
  *) exit 0 ;;
esac

plugin_root=${PLUGIN_ROOT:-${CLAUDE_PLUGIN_ROOT:-}}
if [ -z "$plugin_root" ]; then
  echo "jaipilot: plugin root is unavailable to the post-commit hook" >&2
  exit 1
fi

printf '%s' "$hook_input" | "$plugin_root/bin/jaipilot" hook-post-commit --project .
