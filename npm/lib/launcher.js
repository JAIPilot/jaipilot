import { existsSync, readFileSync, realpathSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(moduleDirectory, "../..");
const packageMetadata = JSON.parse(readFileSync(join(packageRoot, "package.json"), "utf8"));

export function platformClassifier(platform = process.platform, architecture = process.arch) {
  const operatingSystem = platform === "darwin" ? "macos" : platform === "linux" ? "linux" : null;
  const processor = architecture === "x64" ? "x64" : architecture === "arm64" ? "aarch64" : null;
  if (operatingSystem === null || processor === null) {
    throw new Error(`unsupported platform: ${platform}-${architecture}`);
  }
  return `${operatingSystem}-${processor}`;
}

export function resolveAppDirectory(environment = process.env, userHome = homedir()) {
  if (environment.JAIPILOT_NPM_HOME) {
    return resolve(environment.JAIPILOT_NPM_HOME);
  }
  const dataRoot = environment.XDG_DATA_HOME
    ? resolve(environment.XDG_DATA_HOME)
    : join(userHome, ".local", "share");
  return join(dataRoot, "jaipilot", "npm");
}

export function compareVersions(left, right) {
  const leftParts = parseVersion(left);
  const rightParts = parseVersion(right);
  for (let index = 0; index < 3; index += 1) {
    const comparison = leftParts[index] - rightParts[index];
    if (comparison !== 0) {
      return comparison;
    }
  }
  return 0;
}

export function installedVersion(appDirectory) {
  const current = join(appDirectory, "current");
  try {
    const versionDirectory = realpathSync(current);
    const version = versionDirectory.split(/[\\/]/).at(-1);
    return /^\d+\.\d+\.\d+$/.test(version) && existsSync(join(versionDirectory, "bin", "jaipilot"))
      ? version
      : null;
  } catch {
    return null;
  }
}

export function buildInstallArguments(version, appDirectory, environment = process.env) {
  const archiveUrl = environment.JAIPILOT_NPM_ARCHIVE_URL;
  const checksumUrl = environment.JAIPILOT_NPM_CHECKSUM_URL;
  if ((archiveUrl && !checksumUrl) || (!archiveUrl && checksumUrl)) {
    throw new Error("JAIPILOT_NPM_ARCHIVE_URL and JAIPILOT_NPM_CHECKSUM_URL must be set together");
  }
  const args = [
    join(packageRoot, "install.sh"),
    "--version",
    version,
    "--app-dir",
    appDirectory,
    "--no-bin-link"
  ];
  if (archiveUrl) {
    args.push("--archive-url", archiveUrl, "--checksum-url", checksumUrl);
  }
  return args;
}

export async function main(args, environment = process.env) {
  platformClassifier();
  const packageVersion = packageMetadata.version;
  const appDirectory = resolveAppDirectory(environment);
  const currentVersion = installedVersion(appDirectory);
  if (currentVersion === null || compareVersions(currentVersion, packageVersion) < 0) {
    const installStatus = await run("sh", buildInstallArguments(packageVersion, appDirectory, environment), environment);
    if (installStatus !== 0) {
      throw new Error(`installation failed with exit code ${installStatus}`);
    }
  }

  const executable = join(appDirectory, "current", "bin", "jaipilot");
  if (!existsSync(executable)) {
    throw new Error(`installed launcher was not found at ${executable}`);
  }
  return run(executable, args, environment);
}

function parseVersion(value) {
  if (!/^\d+\.\d+\.\d+$/.test(value)) {
    throw new Error(`invalid semantic version: ${value}`);
  }
  return value.split(".").map(Number);
}

function run(command, args, environment) {
  return new Promise((resolveStatus, reject) => {
    const child = spawn(command, args, {
      env: environment,
      stdio: "inherit"
    });
    child.once("error", reject);
    child.once("exit", (status, signal) => {
      if (signal) {
        reject(new Error(`${command} terminated by ${signal}`));
        return;
      }
      resolveStatus(status ?? 1);
    });
  });
}
