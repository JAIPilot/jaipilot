#!/bin/sh
set -eu
[ -z "$(gofmt -l cmd internal)" ] || { echo "Run gofmt before committing." >&2; exit 1; }
go vet ./...
go test -race ./...
mkdir -p dist
extension=""
case "${OS:-}" in Windows_NT) extension=.exe ;; esac
CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o "dist/jaipilot$extension" ./cmd/jaipilot
CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o "dist/jaipilot-acp$extension" ./cmd/jaipilot-acp
node scripts/check-mcp.mjs "dist/jaipilot$extension"
node scripts/check-acp.mjs "dist/jaipilot-acp$extension"
sh -n install.sh
node --check npm/install.mjs
node --check npm/bin/jaipilot.cjs
node --test scripts/install.test.mjs
