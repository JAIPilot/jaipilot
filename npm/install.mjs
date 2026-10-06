import { createHash } from "node:crypto";
import { chmod, mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const root = fileURLToPath(new URL(".", import.meta.url));
const { version } = JSON.parse(await readFile(join(root, "package.json"), "utf8"));
const platforms = { darwin: "apple-darwin", linux: "unknown-linux-gnu", win32: "pc-windows-msvc" };
const architectures = { x64: "x86_64", arm64: "aarch64" };
const platform = platforms[process.platform];
const architecture = architectures[process.arch];
if (!platform || !architecture || (process.platform === "win32" && process.arch !== "x64")) {
  throw new Error(`Unsupported JAIPilot platform: ${process.platform}/${process.arch}`);
}
const extension = process.platform === "win32" ? ".exe" : "";
const asset = `jaipilot-${architecture}-${platform}${extension}`;
const base = `https://github.com/JAIPilot/jaipilot/releases/download/v${version}`;
const destination = join(root, "bin", `jaipilot-native${extension}`);
const temporary = `${destination}.${process.pid}.tmp${extension}`;
async function download(name) {
  const response = await fetch(`${base}/${name}`, { signal: AbortSignal.timeout(180_000) });
  if (!response.ok) throw new Error(`JAIPilot download failed: ${name} (HTTP ${response.status})`);
  return Buffer.from(await response.arrayBuffer());
}
try {
  const [binary, checksum] = await Promise.all([download(asset), download(`${asset}.sha256`)]);
  const expected = checksum.toString("utf8").trim().split(/\s+/)[0];
  if (
    !/^[0-9a-f]{64}$/.test(expected) ||
    createHash("sha256").update(binary).digest("hex") !== expected
  ) {
    throw new Error("JAIPilot checksum mismatch; installation stopped");
  }
  await mkdir(join(root, "bin"), { recursive: true });
  await writeFile(temporary, binary, { mode: 0o755 });
  await chmod(temporary, 0o755);
  const result = spawnSync(temporary, ["--version"], { encoding: "utf8", timeout: 30_000 });
  if (result.status !== 0 || result.stdout.trim() !== `JAIPilot CLI ${version}`) {
    throw new Error("Downloaded JAIPilot failed its version check");
  }
  await rename(temporary, destination);
} finally {
  await rm(temporary, { force: true });
}
