import assert from "node:assert/strict";
import { join, resolve } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { spawnSync } from "node:child_process";

// Check the compiled distribution's real stdio framing, tool schemas, auth failure and shutdown.
const binary = resolve(process.argv[2] ?? "dist/jaipilot");
const root = await mkdtemp(join(tmpdir(), "jaipilot-mcp-check-"));
await writeFile(join(root, "Order.java"), "public class Order {}\n");
const init = spawnSync("git", ["-C", root, "init", "-q"]);
assert.equal(init.status, 0);
const transport = new StdioClientTransport({
  command: binary,
  args: ["mcp", "--repo", root],
  // Deno's Node shim can miss Windows' mixed-case Path when the SDK builds its allowlist.
  env: { JAIPILOT_CONFIG_DIR: root, PATH: process.env.PATH ?? "" },
  stderr: "pipe",
});
transport.stderr?.on("data", (chunk) => {
  console.error(new TextDecoder().decode(chunk).trimEnd());
});
const client = new Client({ name: "jaipilot-release-check", version: "1" });
try {
  await client.connect(transport);
  assert.equal(client.getServerVersion()?.name, "jaipilot");
  assert.match(client.getInstructions(), /Before editing/);
  const catalog = await client.listTools();
  assert.deepEqual(catalog.tools.map((tool) => tool.name), [
    "lock_behavior",
    "verify_behavior",
    "get_job_status",
    "cancel_job",
  ]);
  const started = await client.callTool({
    name: "lock_behavior",
    arguments: {
      scope: { paths: ["Order.java"] },
      test_paths: ["tests"],
      test_command: "never executed",
    },
  });
  const job = started.structuredContent;
  const final = (await client.callTool({
    name: "get_job_status",
    arguments: { job_id: job.job_id, wait_seconds: 10 },
  })).structuredContent;
  assert.equal(final.state, "failed", JSON.stringify(final));
  assert.equal(final.ready_to_edit, false);
  assert.match(final.error, /Sign in first/);
  console.log(
    `Packaged MCP ${client.getServerVersion()?.version}: handshake, schemas and auth gate passed.`,
  );
} finally {
  await client.close();
  await rm(root, { recursive: true, force: true });
}
