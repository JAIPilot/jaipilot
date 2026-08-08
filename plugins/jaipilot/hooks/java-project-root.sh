#!/usr/bin/env sh
set -eu

start=${1:-.}
[ -d "$start" ] || exit 1
start=$(CDPATH= cd -- "$start" 2>/dev/null && pwd -P) || exit 1

root=""
if command -v git >/dev/null 2>&1; then
  root=$(git -C "$start" --no-optional-locks rev-parse --show-toplevel 2>/dev/null || true)
fi

if [ -z "$root" ]; then
  candidate=$start
  while :; do
    if [ -f "$candidate/pom.xml" ] \
      || [ -f "$candidate/build.gradle" ] \
      || [ -f "$candidate/build.gradle.kts" ] \
      || [ -f "$candidate/settings.gradle" ] \
      || [ -f "$candidate/settings.gradle.kts" ]; then
      root=$candidate
      break
    fi
    parent=$(dirname -- "$candidate")
    [ "$parent" != "$candidate" ] || break
    candidate=$parent
  done
fi

[ -n "$root" ] || exit 1
root=$(CDPATH= cd -- "$root" 2>/dev/null && pwd -P) || exit 1

build_file=$(find "$root" \
  -type d \( -name .git -o -name .gradle -o -name node_modules \) -prune -o \
  -type f \( -name pom.xml -o -name build.gradle -o -name build.gradle.kts \
    -o -name settings.gradle -o -name settings.gradle.kts \) -print -quit 2>/dev/null || true)
[ -n "$build_file" ] || exit 1

java_file=$(find "$root" \
  -type d \( -name .git -o -name .gradle -o -name node_modules \) -prune -o \
  -type f -name '*.java' \
  \( -path '*/src/main/java/*' -o -path '*/src/test/java/*' \) \
  -print -quit 2>/dev/null || true)
[ -n "$java_file" ] || exit 1

printf '%s\n' "$root"
