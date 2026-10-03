import assert from "node:assert/strict";
import { runCommand } from "../cli/agent.ts";

Deno.test("local runner returns real exit status and bounded output", async () => {
  const root = await Deno.makeTempDir();
  try {
    const passed = await runCommand(root, {
      command: Deno.build.os === "windows" ? "echo JAIPilot" : "printf JAIPilot",
      purpose: "test",
      timeoutSeconds: 7200,
    });
    assert.equal(passed.exitCode, 0);
    assert.match(passed.output, /JAIPilot/);
    assert.equal(passed.timedOut, false);
    const failed = await runCommand(root, {
      command: Deno.build.os === "windows" ? "exit /b 3" : "exit 3",
      purpose: "test",
      timeoutSeconds: 5,
    });
    assert.equal(failed.exitCode, 3);
    await assert.rejects(() => runCommand(root, { command: "", timeoutSeconds: 5 }));
    await assert.rejects(() => runCommand(root, { command: "echo test", timeoutSeconds: 7201 }));
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
