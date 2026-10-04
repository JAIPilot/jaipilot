import assert from "node:assert/strict";
import { resolve } from "node:path";
import * as acp from "npm:@agentclientprotocol/sdk@1.7.0";

// Verify the shipped executable in a fresh client, without opening a login or spending credits.
const binary = resolve(Deno.args[0] ?? "dist/jaipilot-acp");
const config = await Deno.makeTempDir();
const child = new Deno.Command(binary, {
  args: ["acp"],
  env: { JAIPILOT_CONFIG_DIR: config },
  stdin: "piped",
  stdout: "piped",
  stderr: "piped",
}).spawn();
const stderr = new Response(child.stderr).text();
const updates: acp.SessionNotification[] = [];
const client = acp.client({ name: "jaipilot-release-check" })
  .onNotification("session/update", ({ params }) => {
    updates.push(params);
  })
  .connect(acp.ndJsonStream(child.stdin, child.stdout));
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
      cwd: Deno.cwd(),
      mcpServers: [],
    }), (error: unknown) => error instanceof acp.RequestError && error.code === -32000);
  assert.equal(updates.length, 0);
  console.log(`Packaged ACP ${init.agentInfo?.version}: handshake and authentication gate passed.`);
} finally {
  clearTimeout(timeout);
  client.close();
  try {
    child.kill();
  } catch { /* exited */ }
  await child.status;
  await client.closed;
  const diagnostics = await stderr;
  if (diagnostics.trim()) console.error(diagnostics.trim());
  await Deno.remove(config, { recursive: true });
}
