import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { join } from "node:path";
import { newerVersion, shouldAutoUpdate, updateCli, type UpdateOptions } from "../cli/update.ts";

const bytes = new TextEncoder().encode("mock release binary");
const assetName = `jaipilot-${Deno.build.target}${Deno.build.os === "windows" ? ".exe" : ""}`;
const release = () => ({
  tag_name: "v1.1.0",
  draft: false,
  prerelease: false,
  assets: [{
    name: assetName,
    browser_download_url:
      `https://github.com/JAIPilot/jaipilot/releases/download/v1.1.0/${assetName}`,
    digest: `sha256:${createHash("sha256").update(bytes).digest("hex")}`,
    size: bytes.length,
  }],
});

async function fixture(
  run: (value: {
    root: string;
    executable: string;
    release: ReturnType<typeof release>;
    reports: string[];
    calls: { command: string; args: string[]; env?: Record<string, string> }[];
    options: UpdateOptions;
  }) => Promise<void>,
) {
  const root = await Deno.makeTempDir({ prefix: "jaipilot update " });
  const executable = join(root, "jaipilot");
  await Deno.writeTextFile(executable, "existing installation");
  const metadata = release();
  const reports: string[] = [];
  const calls: { command: string; args: string[]; env?: Record<string, string> }[] = [];
  const options: UpdateOptions = {
    version: "1.0.0",
    standalone: true,
    executable,
    fetcher: (url) =>
      Promise.resolve(
        String(url).endsWith("/releases/latest") ? Response.json(metadata) : new Response(bytes),
      ),
    report: (message) => reports.push(message),
    execute: (command, process) => {
      calls.push({ command, args: process.args, env: process.env });
      return Promise.resolve({
        code: process.capture || process.args[0] === "install" ? 0 : 2,
        stdout: "JAIPilot CLI 1.1.0",
      });
    },
  };
  try {
    await run({ root, executable, release: metadata, reports, calls, options });
  } finally {
    await Deno.remove(root, { recursive: true });
  }
}

Deno.test("update compares stable versions numerically and never downgrades", () => {
  assert.equal(newerVersion("1.10.0", "1.9.9"), true);
  assert.equal(newerVersion("2.0.0", "1.99.99"), true);
  assert.equal(newerVersion("1.1.0", "1.1.0"), false);
  assert.equal(newerVersion("1.0.9", "1.1.0"), false);
  assert.throws(() => newerVersion("1.2.0-beta", "1.1.0"));
});

Deno.test("only workflow commands auto-update and an opt-out disables them", () => {
  assert.equal(shouldAutoUpdate(["run", "generate_tests"], ""), true);
  assert.equal(shouldAutoUpdate(["workflows"], ""), true);
  for (const command of ["auth", "update", "--version", "version", "help", "--help", "-h"]) {
    assert.equal(shouldAutoUpdate([command], ""), false);
  }
  assert.equal(shouldAutoUpdate(["run"], "1"), false);
});

Deno.test("binary update verifies, replaces, restarts once and preserves arguments and exit code", () =>
  fixture(async ({ root, executable, calls, options }) => {
    const args = ["run", "generate_tests", "--repo", "/a path/with spaces", "--json"];
    assert.equal(await updateCli(args, { ...options, automatic: true }), 2);
    assert.deepEqual(await Deno.readFile(executable), bytes);
    assert.equal(calls.length, 2);
    assert.deepEqual(calls[0].args, ["--version"]);
    assert.deepEqual(calls[1].args, args);
    assert.equal(calls[1].command, await Deno.realPath(executable));
    assert.equal(calls[1].env?.JAIPILOT_NO_UPDATE, "1");
    const files = [];
    for await (const file of Deno.readDir(root)) files.push(file.name);
    assert.deepEqual(files, ["jaipilot"]);
  }));

Deno.test("manual update installs without running a workflow", () =>
  fixture(async ({ calls, options }) => {
    assert.equal(await updateCli([], options), null);
    assert.equal(calls.length, 1);
  }));

Deno.test("up-to-date and newer local versions skip downloads", () =>
  fixture(async ({ executable, calls, options }) => {
    for (const version of ["1.1.0", "2.0.0"]) {
      await updateCli([], { ...options, version });
    }
    assert.equal(calls.length, 0);
    assert.equal(await Deno.readTextFile(executable), "existing installation");
  }));

Deno.test("check-only reports an update without changing the installation", () =>
  fixture(async ({ executable, calls, reports, options }) => {
    await updateCli([], { ...options, checkOnly: true });
    assert.match(reports[0], /1.1.0 is available/);
    assert.equal(calls.length, 0);
    assert.equal(await Deno.readTextFile(executable), "existing installation");
  }));

Deno.test("offline auto-update continues but explicit update reports failure", () =>
  fixture(async ({ options, reports }) => {
    const offline = { ...options, fetcher: () => Promise.reject(new Error("offline")) };
    assert.equal(await updateCli(["run"], { ...offline, automatic: true }), null);
    assert.match(reports[0], /update skipped: offline/);
    await assert.rejects(() => updateCli([], offline), /offline/);
  }));

Deno.test("checksum mismatch, truncated and oversized downloads leave the old binary intact", () =>
  fixture(async ({ executable, release, options, calls }) => {
    for (const data of [new Uint8Array(bytes.length), bytes.subarray(1), new Uint8Array(100)]) {
      await assert.rejects(() =>
        updateCli([], {
          ...options,
          fetcher: (url) =>
            Promise.resolve(
              String(url).endsWith("/releases/latest")
                ? Response.json(release)
                : new Response(data),
            ),
        })
      );
      assert.equal(await Deno.readTextFile(executable), "existing installation");
    }
    assert.equal(calls.length, 0);
  }));

Deno.test("download errors and unexpected binary versions preserve the installation", () =>
  fixture(async ({ executable, release, options }) => {
    await assert.rejects(() =>
      updateCli([], {
        ...options,
        fetcher: (url) =>
          Promise.resolve(
            String(url).endsWith("/releases/latest")
              ? Response.json(release)
              : new Response("failed", { status: 503 }),
          ),
      }), /Download failed/);
    await assert.rejects(() =>
      updateCli([], {
        ...options,
        execute: () => Promise.resolve({ code: 0, stdout: "unexpected version" }),
      }), /version check/);
    assert.equal(await Deno.readTextFile(executable), "existing installation");
  }));

Deno.test("untrusted, incomplete and prerelease metadata cannot replace the executable", () =>
  fixture(async ({ executable, release, options }) => {
    const invalid = [
      { ...release, prerelease: true },
      { ...release, draft: true },
      { ...release, tag_name: "v1.2.0-beta" },
      { ...release, assets: [] },
      { ...release, assets: [{ ...release.assets[0], digest: null }] },
      {
        ...release,
        assets: [{ ...release.assets[0], browser_download_url: "https://example.com" }],
      },
      { ...release, assets: [{ ...release.assets[0], size: 300 * 1024 * 1024 }] },
    ];
    for (const value of invalid) {
      await assert.rejects(() =>
        updateCli([], { ...options, fetcher: () => Promise.resolve(Response.json(value)) })
      );
    }
    assert.equal(await Deno.readTextFile(executable), "existing installation");
  }));

Deno.test("concurrent updater does not download or delete another updater's lock", () =>
  fixture(async ({ executable, options, calls }) => {
    await Deno.writeTextFile(`${executable}.update.lock`, "another updater");
    assert.equal(await updateCli(["run"], { ...options, automatic: true }), null);
    await assert.rejects(() => updateCli([], options), /Cannot lock/);
    assert.equal(calls.length, 0);
    assert.equal(await Deno.readTextFile(`${executable}.update.lock`), "another updater");
  }));

Deno.test("source updates reinstall the pinned Deno launcher without touching Deno or the checkout", () =>
  fixture(async ({ executable, options, calls }) => {
    const args = ["run", "generate_tests", "--class", "com.acme.Order"];
    assert.equal(await updateCli(args, { ...options, standalone: false, automatic: true }), 2);
    assert.equal(calls[0].command, executable);
    assert.deepEqual(calls[0].args.slice(0, 7), [
      "install",
      "-g",
      "-A",
      "--force",
      "--reload",
      "--no-config",
      "--no-lock",
    ]);
    assert.equal(
      calls[0].args.at(-1),
      "https://raw.githubusercontent.com/JAIPilot/jaipilot/v1.1.0/cli/main.ts",
    );
    assert.deepEqual(calls[1].args.slice(-args.length), args);
    assert.equal(await Deno.readTextFile(executable), "existing installation");
  }));

Deno.test("real compiled CLI replaces its running executable and returns the restarted exit code", async () => {
  const root = await Deno.makeTempDir({ prefix: "jaipilot executable " });
  const decode = (bytes: Uint8Array) => new TextDecoder().decode(bytes);
  try {
    const extension = Deno.build.os === "windows" ? ".exe" : "";
    const oldBinary = join(root, `old${extension}`);
    const newBinary = join(root, `new${extension}`);
    const oldSource = join(root, "old.ts");
    const newSource = join(root, "new.ts");
    await Deno.writeTextFile(
      newSource,
      `
      if (Deno.args[0] === "--version") console.log("JAIPilot CLI 1.1.0");
      else { console.log(JSON.stringify({args:Deno.args,cwd:Deno.cwd()})); Deno.exitCode=2; }
    `,
    );
    for (const name of ["update.ts", "version.ts"]) {
      await Deno.copyFile(new URL(`../cli/${name}`, import.meta.url), join(root, name));
    }
    await Deno.writeTextFile(
      oldSource,
      `
      import {updateCli} from "./update.ts";
      import {createHash} from "node:crypto";
      const bytes=await Deno.readFile(Deno.env.get("JAIPILOT_TEST_BINARY")!);
      const name="jaipilot-"+Deno.build.target+(Deno.build.os === "windows" ? ".exe" : "");
      const metadata={tag_name:"v1.1.0",draft:false,prerelease:false,assets:[{
        name,browser_download_url:"https://github.com/JAIPilot/jaipilot/releases/download/v1.1.0/"+name,
        size:bytes.length,digest:"sha256:"+createHash("sha256").update(bytes).digest("hex")
      }]};
      Deno.exitCode=(await updateCli(Deno.args,{automatic:true,version:"1.0.0",
        fetcher:async url => String(url).endsWith("/releases/latest")
          ? Response.json(metadata) : new Response(bytes)
      })) ?? 99;
    `,
    );
    for (const [source, binary] of [[oldSource, oldBinary], [newSource, newBinary]]) {
      const compiled = await new Deno.Command(Deno.execPath(), {
        args: ["compile", "-A", "--no-config", "--no-lock", "--output", binary, source],
        stdout: "piped",
        stderr: "piped",
      }).output();
      assert.equal(compiled.code, 0, decode(compiled.stderr));
    }
    const args = ["run", "generate_tests", "--repo", root, "--class", "a.b.C", "--json"];
    const result = await new Deno.Command(oldBinary, {
      args,
      cwd: root,
      env: { JAIPILOT_TEST_BINARY: newBinary },
      stdout: "piped",
      stderr: "piped",
    }).output();
    assert.equal(result.code, 2, decode(result.stderr));
    const restarted = JSON.parse(decode(result.stdout));
    assert.deepEqual(restarted.args, args);
    assert.equal(await Deno.realPath(restarted.cwd), await Deno.realPath(root));
    const version = await new Deno.Command(oldBinary, { args: ["--version"] }).output();
    assert.equal(decode(version.stdout).trim(), "JAIPilot CLI 1.1.0");
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
