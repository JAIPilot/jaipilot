import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

Deno.test({
  name:
    "curl installer selects platform, verifies checksum and preserves existing binaries on failure",
  ignore: Deno.build.os === "windows",
  async fn() {
    const root = await Deno.makeTempDir({ prefix: "jaipilot installer " });
    try {
      const mockBin = join(root, "mock-bin");
      const installDir = join(root, "installed bin");
      await Deno.mkdir(mockBin);
      await Deno.mkdir(installDir);
      const binary = "#!/bin/sh\nprintf 'JAIPilot CLI 1.0.0\\n'\n";
      await Deno.writeTextFile(join(root, "binary"), binary);
      await Deno.writeTextFile(
        join(root, "checksum"),
        `${createHash("sha256").update(binary).digest("hex")}  binary\n`,
      );
      await Deno.writeTextFile(
        join(mockBin, "curl"),
        `#!/bin/sh
        output=""
        url=""
        while [ "$#" -gt 0 ]; do
          case "$1" in
            -o) output="$2"; shift ;;
            https://*) url="$1" ;;
          esac
          shift
        done
        printf '%s\n' "$url" >> "$JAIPILOT_TEST_FIXTURE/urls"
        case "$url" in
          */releases/latest) printf 'https://github.com/JAIPilot/jaipilot/releases/tag/v1.0.0' ;;
          *.sha256) cp "$JAIPILOT_TEST_FIXTURE/checksum" "$output" ;;
          *) cp "$JAIPILOT_TEST_FIXTURE/binary" "$output" ;;
        esac
      `,
      );
      await Deno.writeTextFile(
        join(mockBin, "uname"),
        `#!/bin/sh
        case "$1" in -s) printf '%s\n' "$JAIPILOT_TEST_OS" ;; -m) printf '%s\n' "$JAIPILOT_TEST_ARCH" ;; esac
      `,
      );
      await Deno.chmod(join(mockBin, "curl"), 0o755);
      await Deno.chmod(join(mockBin, "uname"), 0o755);
      const run = (os: string, architecture: string, pinned = "") =>
        new Deno.Command("sh", {
          args: [fileURLToPath(new URL("../install.sh", import.meta.url))],
          env: {
            PATH: `${mockBin}:${Deno.env.get("PATH")}`,
            JAIPILOT_INSTALL_DIR: installDir,
            JAIPILOT_VERSION: pinned,
            JAIPILOT_TEST_FIXTURE: root,
            JAIPILOT_TEST_OS: os,
            JAIPILOT_TEST_ARCH: architecture,
          },
          stdout: "piped",
          stderr: "piped",
        }).output();
      const decode = (bytes: Uint8Array) => new TextDecoder().decode(bytes);
      const installed = await run("Darwin", "arm64");
      assert.equal(installed.code, 0, decode(installed.stderr));
      assert.match(
        await Deno.readTextFile(join(root, "urls")),
        /jaipilot-aarch64-apple-darwin\.sha256/,
        decode(installed.stderr),
      );
      assert.equal(await Deno.readTextFile(join(installDir, "jaipilot")), binary);
      await Deno.writeTextFile(join(root, "urls"), "");
      const linux = await run("Linux", "x86_64", "1.0.0");
      assert.equal(linux.code, 0, decode(linux.stderr));
      const urls = await Deno.readTextFile(join(root, "urls"));
      assert.match(urls, /jaipilot-x86_64-unknown-linux-gnu\.sha256/);
      assert.doesNotMatch(urls, /releases\/latest/);
      await Deno.writeTextFile(join(root, "checksum"), `${"0".repeat(64)}  binary\n`);
      const corrupt = await run("Linux", "x86_64");
      assert.notEqual(corrupt.code, 0);
      assert.match(decode(corrupt.stderr), /Checksum mismatch/);
      assert.equal(await Deno.readTextFile(join(installDir, "jaipilot")), binary);
      assert.notEqual((await run("Linux", "riscv64")).code, 0);
      const files = [];
      for await (const file of Deno.readDir(installDir)) files.push(file.name);
      assert.deepEqual(files, ["jaipilot"]);
    } finally {
      await Deno.remove(root, { recursive: true });
    }
  },
});
