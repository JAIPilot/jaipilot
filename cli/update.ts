import { createHash } from "node:crypto";
import { dirname } from "node:path";
import { VERSION } from "./version.ts";

const REPOSITORY = "JAIPilot/jaipilot";
const LATEST_RELEASE = `https://api.github.com/repos/${REPOSITORY}/releases/latest`;
const MAX_BINARY_SIZE = 256 * 1024 * 1024;

type Asset = { name: string; browser_download_url: string; digest: string; size: number };
type Release = { tag_name: string; draft: boolean; prerelease: boolean; assets: Asset[] };
type ProcessOptions = { args: string[]; env?: Record<string, string>; capture?: boolean };
type ProcessResult = { code: number; stdout: string };

export type UpdateOptions = {
  automatic?: boolean;
  checkOnly?: boolean;
  version?: string;
  executable?: string;
  standalone?: boolean;
  target?: string;
  os?: string;
  fetcher?: typeof fetch;
  report?: (message: string) => void;
  execute?: (command: string, options: ProcessOptions) => Promise<ProcessResult>;
};

export function newerVersion(candidate: string, current: string): boolean {
  const parse = (value: string) => {
    if (!/^\d+\.\d+\.\d+$/.test(value)) throw new Error(`Invalid release version: ${value}`);
    return value.split(".").map(Number);
  };
  const next = parse(candidate);
  const previous = parse(current);
  for (let i = 0; i < next.length; i++) {
    if (next[i] !== previous[i]) return next[i] > previous[i];
  }
  return false;
}

export function shouldAutoUpdate(args: string[], disabled = Deno.env.get("JAIPILOT_NO_UPDATE")) {
  return disabled !== "1" && ["run", "workflows"].includes(args[0]);
}

async function execute(command: string, options: ProcessOptions): Promise<ProcessResult> {
  const child = new Deno.Command(command, {
    args: options.args,
    env: options.env,
    stdin: options.capture ? "null" : "inherit",
    stdout: options.capture ? "piped" : "inherit",
    stderr: options.capture ? "piped" : "inherit",
    ...(options.capture ? { signal: AbortSignal.timeout(10_000) } : {}),
  }).spawn();
  if (options.capture) {
    const result = await child.output();
    return { code: result.code, stdout: new TextDecoder().decode(result.stdout).trim() };
  }
  return { code: (await child.status).code, stdout: "" };
}

async function download(
  asset: Asset,
  temporary: string,
  fetcher: typeof fetch,
): Promise<void> {
  const response = await fetcher(asset.browser_download_url, {
    signal: AbortSignal.timeout(120_000),
  });
  if (!response.ok || !response.body) throw new Error(`Download failed (HTTP ${response.status})`);
  const hash = createHash("sha256");
  let size = 0;
  const file = await Deno.open(temporary, { write: true, truncate: true });
  try {
    await response.body.pipeTo(
      new WritableStream<Uint8Array>({
        async write(chunk) {
          size += chunk.length;
          if (size > asset.size) throw new Error("Release binary exceeds its advertised size");
          hash.update(chunk);
          let written = 0;
          while (written < chunk.length) written += await file.write(chunk.subarray(written));
        },
      }),
    );
  } finally {
    file.close();
  }
  if (size !== asset.size || `sha256:${hash.digest("hex")}` !== asset.digest) {
    throw new Error("Release binary checksum or size did not match; installation unchanged");
  }
}

async function installBinary(
  release: Release,
  version: string,
  options: UpdateOptions,
): Promise<string | null> {
  const target = options.target ?? Deno.build.target;
  const os = options.os ?? Deno.build.os;
  const name = `jaipilot-${target}${os === "windows" ? ".exe" : ""}`;
  const asset = release.assets.find((item) => item.name === name);
  const expectedUrl =
    `https://github.com/${REPOSITORY}/releases/download/${release.tag_name}/${name}`;
  if (
    !asset || asset.browser_download_url !== expectedUrl ||
    !/^sha256:[0-9a-f]{64}$/.test(asset.digest) ||
    !Number.isSafeInteger(asset.size) || asset.size <= 0 || asset.size > MAX_BINARY_SIZE
  ) throw new Error(`A verified release binary is not available yet for ${target}`);

  const executable = await Deno.realPath(options.executable ?? Deno.execPath());
  const lockPath = `${executable}.update.lock`;
  let lock: Deno.FsFile;
  try {
    const info = await Deno.stat(lockPath).catch(() => null);
    if (info?.mtime && Date.now() - info.mtime.getTime() > 10 * 60_000) {
      await Deno.remove(lockPath);
    }
    lock = await Deno.open(lockPath, { createNew: true, write: true, mode: 0o600 });
  } catch (error) {
    if (error instanceof Deno.errors.AlreadyExists && options.automatic) return null;
    throw new Error(
      "Cannot lock the CLI installation; another update may be running or its directory is not writable",
    );
  }
  let temporary: string | undefined;
  const backup = `${executable}.previous`;
  try {
    temporary = await Deno.makeTempFile({
      dir: dirname(executable),
      prefix: ".jaipilot-update-",
      suffix: os === "windows" ? ".exe" : "",
    });
    await download(asset, temporary, options.fetcher ?? fetch);
    if (os !== "windows") await Deno.chmod(temporary, 0o755);
    const checked = await (options.execute ?? execute)(temporary, {
      args: ["--version"],
      capture: true,
      env: { JAIPILOT_NO_UPDATE: "1" },
    });
    if (checked.code !== 0 || checked.stdout !== `JAIPilot CLI ${version}`) {
      throw new Error("Downloaded CLI failed its version check; installation unchanged");
    }
    if (os === "windows") {
      // Windows cannot overwrite a running executable. Move it aside first and restore on failure.
      await Deno.remove(backup).catch((error) => {
        if (!(error instanceof Deno.errors.NotFound)) throw error;
      });
      await Deno.rename(executable, backup);
      try {
        await Deno.rename(temporary, executable);
      } catch (error) {
        await Deno.rename(backup, executable);
        throw error;
      }
      await Deno.remove(backup).catch(() => {});
    } else {
      await Deno.rename(temporary, executable);
    }
    return executable;
  } finally {
    if (temporary) await Deno.remove(temporary).catch(() => {});
    lock.close();
    await Deno.remove(lockPath).catch(() => {});
  }
}

/** Returns the restarted command's exit code, or null when the caller should continue. */
export async function updateCli(
  args: string[],
  options: UpdateOptions = {},
): Promise<number | null> {
  const report = options.report ?? console.error;
  try {
    const response = await (options.fetcher ?? fetch)(LATEST_RELEASE, {
      headers: { accept: "application/vnd.github+json", "user-agent": `JAIPilot/${VERSION}` },
      signal: AbortSignal.timeout(options.automatic ? 3_000 : 15_000),
    });
    if (!response.ok) throw new Error(`Release check failed (HTTP ${response.status})`);
    const release = await response.json() as Release;
    if (
      !/^v\d+\.\d+\.\d+$/.test(release.tag_name) || release.draft || release.prerelease ||
      !Array.isArray(release.assets)
    ) throw new Error("GitHub returned an invalid stable release");
    const version = release.tag_name.slice(1);
    if (!newerVersion(version, options.version ?? VERSION)) {
      if (!options.automatic) report(`JAIPilot CLI ${options.version ?? VERSION} is up to date.`);
      return null;
    }
    if (options.checkOnly) {
      report(`JAIPilot CLI ${version} is available; run \`jaipilot update\`.`);
      return null;
    }
    report(`Updating JAIPilot CLI to ${version}…`);
    const run = options.execute ?? execute;
    const standalone = options.standalone ?? Deno.build.standalone;
    let command: string;
    let restartArgs: string[];
    if (standalone) {
      const installed = await installBinary(release, version, options);
      if (!installed) return null;
      command = installed;
      restartArgs = args;
    } else {
      // Replace only Deno's installed launcher, never the source checkout or Deno itself.
      const entry =
        `https://raw.githubusercontent.com/${REPOSITORY}/${release.tag_name}/cli/main.ts`;
      command = options.executable ?? Deno.execPath();
      const result = await run(command, {
        args: [
          "install",
          "-g",
          "-A",
          "--force",
          "--reload",
          "--no-config",
          "--no-lock",
          "-n",
          "jaipilot",
          entry,
        ],
      });
      if (result.code !== 0) throw new Error("Could not update the Deno CLI installation");
      restartArgs = ["run", "-A", "--no-config", "--no-lock", entry, ...args];
    }
    report(`Installed JAIPilot CLI ${version}.`);
    if (!options.automatic) return null;
    return (await run(command, {
      args: restartArgs,
      env: { JAIPILOT_NO_UPDATE: "1" },
    })).code;
  } catch (error) {
    if (!options.automatic) throw error;
    report(`JAIPilot update skipped: ${error instanceof Error ? error.message : String(error)}`);
    return null;
  }
}
