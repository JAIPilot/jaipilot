import assert from "node:assert/strict";
import { VERSION } from "../cli/version.ts";

Deno.test("release version matches the CLI", async () => {
  assert.equal((await Deno.readTextFile(new URL("../VERSION", import.meta.url))).trim(), VERSION);
  const npm = JSON.parse(await Deno.readTextFile(new URL("../npm/package.json", import.meta.url)));
  assert.equal(npm.version, VERSION);
});
