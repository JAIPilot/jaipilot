import assert from "node:assert/strict";
import { runCommand, workflows } from "../cli/agent.ts";

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

Deno.test("service outages retry but a terminal server response stops", async () => {
  const config = await Deno.makeTempDir();
  const previousConfig = Deno.env.get("JAIPILOT_CONFIG_DIR");
  const originalFetch = globalThis.fetch;
  try {
    Deno.env.set("JAIPILOT_CONFIG_DIR", config);
    await Deno.mkdir(`${config}/jaipilot`);
    await Deno.writeTextFile(
      `${config}/jaipilot/session.json`,
      JSON.stringify({
        access_token: "test-token",
        refresh_token: "test-refresh",
        expires_at: Math.floor(Date.now() / 1000) + 3600,
        email: "test@example.com",
      }),
    );
    let calls = 0;
    globalThis.fetch = (() => {
      calls++;
      return Promise.resolve(
        calls === 1
          ? Response.json({ error: "temporarily unavailable", retryable: true }, {
            status: 503,
            headers: { "Retry-After": "0" },
          })
          : Response.json({ protocolVersion: 6, workflows: [] }),
      );
    }) as typeof fetch;
    assert.deepEqual(await workflows(), []);
    assert.equal(calls, 2);

    calls = 0;
    globalThis.fetch = (() => {
      calls++;
      return calls === 1
        ? Promise.reject(new TypeError("connection lost"))
        : Promise.resolve(Response.json({ protocolVersion: 6, workflows: [] }));
    }) as typeof fetch;
    assert.deepEqual(await workflows(), []);
    assert.equal(calls, 2);

    calls = 0;
    globalThis.fetch = (() => {
      calls++;
      return Promise.resolve(Response.json({ error: "invalid tool result", retryable: false }, {
        status: 502,
      }));
    }) as typeof fetch;
    await assert.rejects(workflows(), /invalid tool result/);
    assert.equal(calls, 1);
  } finally {
    globalThis.fetch = originalFetch;
    if (previousConfig === undefined) Deno.env.delete("JAIPILOT_CONFIG_DIR");
    else Deno.env.set("JAIPILOT_CONFIG_DIR", previousConfig);
    await Deno.remove(config, { recursive: true });
  }
});

Deno.test("local runner preserves quoted executable, script, and argument paths", async () => {
  const root = await Deno.makeTempDir({ prefix: "jaipilot runner spaces " });
  try {
    await Deno.writeTextFile(`${root}/verification with spaces.ts`, "console.log(Deno.args[0]);\n");
    const result = await runCommand(root, {
      command: `"${Deno.execPath()}" run "verification with spaces.ts" "scope with spaces"`,
      timeoutSeconds: 30,
    });
    assert.equal(result.exitCode, 0, result.output);
    assert.equal(result.output.trim(), "scope with spaces");
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
