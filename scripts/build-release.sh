#!/bin/sh
set -eu
target="$1"
case "$target" in
  x86_64-unknown-linux-gnu) os=linux; arch=amd64; extension= ;;
  aarch64-unknown-linux-gnu) os=linux; arch=arm64; extension= ;;
  x86_64-apple-darwin) os=darwin; arch=amd64; extension= ;;
  aarch64-apple-darwin) os=darwin; arch=arm64; extension= ;;
  x86_64-pc-windows-msvc) os=windows; arch=amd64; extension=.exe ;;
  *) echo "Unsupported target" >&2; exit 1 ;;
esac
mkdir -p dist/package
GOOS="$os" GOARCH="$arch" CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o "dist/jaipilot-$target$extension" ./cmd/jaipilot
GOOS="$os" GOARCH="$arch" CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o "dist/package/jaipilot-acp$extension" ./cmd/jaipilot-acp
cp LICENSE THIRD_PARTY_NOTICES.md dist/package/
cp -R licenses dist/package/
(cd dist/package && zip -r -9 "../jaipilot-acp-$target.zip" "jaipilot-acp$extension" LICENSE THIRD_PARTY_NOTICES.md licenses)
(cd dist/package && zip -r -9 "../jaipilot-notices-$target.zip" LICENSE THIRD_PARTY_NOTICES.md licenses)
gzip -9 -n -c "dist/jaipilot-$target$extension" > "dist/jaipilot-$target$extension.gz"
node --input-type=module - "$target" "$extension" <<'JS'
import { stat, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
const [target, extension] = process.argv.slice(2);
const raw = `jaipilot-${target}${extension}`;
if ((await stat(`dist/${raw}`)).size > 12 * 1024 * 1024 || (await stat(`dist/${raw}.gz`)).size > 5 * 1024 * 1024) throw new Error('Native distribution exceeded its size budget');
for (const name of [raw, `${raw}.gz`, `jaipilot-acp-${target}.zip`, `jaipilot-notices-${target}.zip`]) {
  const hash = createHash('sha256').update(await readFile(`dist/${name}`)).digest('hex');
  await writeFile(`dist/${name}.sha256`, `${hash}  ${name}\n`);
}
JS
