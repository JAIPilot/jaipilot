import assert from "node:assert/strict";
import { mkdir, mkdtemp, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import {
  buildInstallArguments,
  compareVersions,
  installedVersion,
  platformClassifier,
  resolveAppDirectory
} from "../lib/launcher.js";

test("platform classifiers match bundled release names", () => {
  assert.equal(platformClassifier("darwin", "arm64"), "macos-aarch64");
  assert.equal(platformClassifier("darwin", "x64"), "macos-x64");
  assert.equal(platformClassifier("linux", "arm64"), "linux-aarch64");
  assert.equal(platformClassifier("linux", "x64"), "linux-x64");
  assert.throws(() => platformClassifier("win32", "x64"), /unsupported platform/);
});

test("npm cache location honors explicit and XDG locations", () => {
  assert.equal(
    resolveAppDirectory({ JAIPILOT_NPM_HOME: "/tmp/custom" }, "/home/example"),
    "/tmp/custom"
  );
  assert.equal(
    resolveAppDirectory({ XDG_DATA_HOME: "/tmp/data" }, "/home/example"),
    "/tmp/data/jaipilot/npm"
  );
  assert.equal(
    resolveAppDirectory({}, "/home/example"),
    "/home/example/.local/share/jaipilot/npm"
  );
});

test("semantic version comparison prevents npm downgrades", () => {
  assert.ok(compareVersions("1.1.0", "1.0.15") > 0);
  assert.ok(compareVersions("2.0.0", "10.0.0") < 0);
  assert.equal(compareVersions("1.0.15", "1.0.15"), 0);
  assert.throws(() => compareVersions("latest", "1.0.0"), /invalid semantic version/);
});

test("installed version comes from a valid current release link", async () => {
  const root = await mkdtemp(join(tmpdir(), "jaipilot-npm-test-"));
  const versionDirectory = join(root, "versions", "1.2.3");
  await mkdir(join(versionDirectory, "bin"), { recursive: true });
  await writeFile(join(versionDirectory, "bin", "jaipilot-mcp"), "launcher\n");
  await symlink("versions/1.2.3", join(root, "current"));

  assert.equal(installedVersion(root), "1.2.3");
});

test("archive overrides must include a checksum and are passed without shell interpolation", () => {
  assert.throws(
    () => buildInstallArguments("1.0.15", "/tmp/app", { JAIPILOT_NPM_ARCHIVE_URL: "file:///archive" }),
    /must be set together/
  );
  const args = buildInstallArguments("1.0.15", "/tmp/app", {
    JAIPILOT_NPM_ARCHIVE_URL: "file:///archive with spaces.tar.gz",
    JAIPILOT_NPM_CHECKSUM_URL: "file:///archive.sha256"
  });
  assert.deepEqual(args.slice(-4), [
    "--archive-url",
    "file:///archive with spaces.tar.gz",
    "--checksum-url",
    "file:///archive.sha256"
  ]);
});
