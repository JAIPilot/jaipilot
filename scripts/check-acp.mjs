import assert from "node:assert/strict";
import { join, resolve } from "node:path";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { Readable, Writable } from "node:stream";
import * as acp from "@agentclientprotocol/sdk";

// Verify the shipped executable in a fresh client, without opening a login or spending credits.
const binary = resolve(process.argv[2] ?? "dist/jaipilot-acp");
const config = await mkdtemp(join(tmpdir(), "jaipilot-acp-check-"));
const child = spawn(binary, ["acp"], { env: { ...process.env, JAIPILOT_CONFIG_DIR: config }, stdio: "pipe" });
let diagnostics = "";
child.stderr.on("data", chunk => { diagnostics += chunk.toString(); });
const exited = new Promise((resolve, reject) => { child.once("exit", resolve); child.once("error", reject); });
const updates = [];
const client = acp.client({ name: "jaipilot-release-check" })
  .onNotification("session/update", ({ params }) => {
    updates.push(params);
  })
  .connect(acp.ndJsonStream(Writable.toWeb(child.stdin), Readable.toWeb(child.stdout)));
const timeout = setTimeout(() => client.close(new Error("ACP handshake timed out")), 20_000);
try {
  const init = await client.agent.request("initialize", {
    protocolVersion: 1,
    clientCapabilities: { terminal: true },
  });
  assert.equal(init.protocolVersion, 1);
  assert.equal(init.agentInfo?.name, "jaipilot");
  assert.equal(init.authMethods?.[0].id, "jaipilot-login");
  await assert.rejects(() =>
    client.agent.request("session/new", {
      cwd: process.cwd(),
      mcpServers: [],
    }), (error) => error instanceof acp.RequestError && error.code === -32000);
  assert.equal(updates.length, 0);
  console.log(`Packaged ACP ${init.agentInfo?.version}: handshake and authentication gate passed.`);
} finally {
  clearTimeout(timeout);
  client.close();
  try {
    child.kill();
  } catch { /* exited */ }
  await exited;
  await client.closed;

  if (diagnostics.trim()) console.error(diagnostics.trim());
  await rm(config, { recursive: true, force: true });
}
