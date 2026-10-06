import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile, mkdir, cp, rm, readdir } from "node:fs/promises";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { createHash } from "node:crypto";
import { gzipSync } from "node:zlib";
import { spawnSync } from "node:child_process";
import { pathToFileURL } from "node:url";

const version = (await readFile(new URL("../VERSION", import.meta.url), "utf8")).trim();
const binary = resolve(`dist/jaipilot${process.platform === "win32" ? ".exe" : ""}`);
const bytes = await readFile(binary);
const compressed = gzipSync(bytes);
const checksum = createHash("sha256").update(compressed).digest("hex");

test("npm postinstall verifies compressed bytes and preserves the installed binary on corruption", async () => {
  const root = await mkdtemp(join(tmpdir(), "jaipilot npm fixture "));
  try {
    await cp("npm", root, { recursive: true });
    await writeFile(join(root, "download.gz"), compressed);
    await writeFile(join(root, "checksum"), checksum);
    await writeFile(join(root, "preload.mjs"), `import { readFile } from "node:fs/promises";
      globalThis.fetch = async url => {
        if (!String(url).startsWith("https://github.com/JAIPilot/jaipilot/releases/download/v${version}/")) throw new Error("Unexpected download URL");
        const data = await readFile(new URL(String(url).endsWith(".sha256") ? "./checksum" : "./download.gz", import.meta.url));
        return { ok: true, status: 200, arrayBuffer: async () => data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) };
      };`);
    const run = () => spawnSync(process.execPath, ["--import", pathToFileURL(join(root, "preload.mjs")).href, join(root, "install.mjs")], { encoding: "utf8" });
    let result = run();
    assert.equal(result.status, 0, result.stderr);
    const installed = join(root, "bin", `jaipilot-native${process.platform === "win32" ? ".exe" : ""}`);
    assert.equal(spawnSync(installed, ["--version"], { encoding: "utf8" }).stdout.trim(), `JAIPilot CLI ${version}`);
    await writeFile(join(root, "checksum"), "0".repeat(64));
    result = run();
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /checksum mismatch/);
    assert.deepEqual(await readFile(installed), bytes);
    assert.ok((await readdir(join(root, "bin"))).every(name => !name.includes(".tmp")));
  } finally { await rm(root, { recursive: true, force: true }); }
});

test("curl installer selects targets, verifies gzip bytes, handles spaces, and preserves an existing binary", { skip: process.platform === "win32" }, async () => {
  const root = await mkdtemp(join(tmpdir(), "jaipilot curl fixture "));
  try {
    const bin = join(root, "mock-bin"), destination = join(root, "installed bin");
    await mkdir(bin); await mkdir(destination);
    await writeFile(join(root, "download.gz"), compressed);
    await writeFile(join(root, "checksum"), checksum);
    await writeFile(join(bin, "curl"), `#!/bin/sh
output=""; url=""
while [ "$#" -gt 0 ]; do
  case "$1" in -o) output="$2"; shift ;; https://*) url="$1" ;; esac
  shift
done
printf '%s\\n' "$url" >> "$JAIPILOT_FIXTURE/urls"
case "$url" in *.sha256) cp "$JAIPILOT_FIXTURE/checksum" "$output" ;; *) cp "$JAIPILOT_FIXTURE/download.gz" "$output" ;; esac
`, { mode: 0o755 });
    await writeFile(join(bin, "uname"), `#!/bin/sh
case "$1" in -s) printf '%s\\n' "$JAIPILOT_FIXTURE_OS" ;; -m) printf '%s\\n' "$JAIPILOT_FIXTURE_ARCH" ;; esac
`, { mode: 0o755 });
    const run = (os, arch, release = version) => spawnSync("sh", [resolve("install.sh")], {
      encoding: "utf8", env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, JAIPILOT_FIXTURE: root,
        JAIPILOT_FIXTURE_OS: os, JAIPILOT_FIXTURE_ARCH: arch, JAIPILOT_VERSION: release, JAIPILOT_INSTALL_DIR: destination },
    });
    for (const [os, arch, target] of [["Darwin", "arm64", "aarch64-apple-darwin"], ["Linux", "x86_64", "x86_64-unknown-linux-gnu"]]) {
      const result = run(os, arch);
      assert.equal(result.status, 0, result.stderr);
      assert.match(await readFile(join(root, "urls"), "utf8"), new RegExp(`jaipilot-${target}\\.gz\\.sha256`));
      assert.deepEqual(await readFile(join(destination, "jaipilot")), bytes);
    }
    await writeFile(join(root, "checksum"), "0".repeat(64));
    const corrupt = run("Linux", "x86_64");
    assert.notEqual(corrupt.status, 0);
    assert.match(corrupt.stderr, /Checksum mismatch/);
    assert.deepEqual(await readFile(join(destination, "jaipilot")), bytes);
    assert.notEqual(run("Linux", "riscv64").status, 0);
    assert.notEqual(run("Linux", "x86_64", "1.2.0-beta").status, 0);
    assert.deepEqual(await readdir(destination), ["jaipilot"]);
  } finally { await rm(root, { recursive: true, force: true }); }
});
