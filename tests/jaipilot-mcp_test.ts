import assert from "node:assert/strict";

const ROOT = new URL("../", import.meta.url);
const PLUGIN = new URL("plugins/jaipilot/", ROOT);
const MCP_URL = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/jaipilot-cloud/mcp";
type McpManifest = {
  mcpServers: Record<string, { type: string; url: string }>;
};

Deno.test("Codex and Claude use the same hosted OAuth-capable MCP resource", async () => {
  const codex = await json<McpManifest>(".codex-plugin/plugin.json");
  const claude = await json<McpManifest>(".mcp.json");
  const expected = { type: "http", url: MCP_URL };
  assert.deepEqual(codex.mcpServers, { "jaipilot-remote": expected });
  assert.deepEqual(claude.mcpServers, { "jaipilot-remote": expected });
});

Deno.test("the distributable contains no provider or shared-secret configuration", async () => {
  const files: string[] = [];
  for await (const entry of walk(PLUGIN)) files.push(entry);
  const text = (await Promise.all(files.map((file) => Deno.readTextFile(file)))).join("\n");
  assert.equal(/JAIPILOT_CLOUD_TRIGGER_SECRET|DAYTONA|GITHUB_APP_PRIVATE_KEY/i.test(text), false);
  assert.equal(files.some((file) => file.endsWith(".ts")), false);
});

async function json<T>(path: string): Promise<T> {
  return JSON.parse(await Deno.readTextFile(new URL(path, PLUGIN)));
}

async function* walk(directory: URL): AsyncGenerator<string> {
  for await (const entry of Deno.readDir(directory)) {
    const child = new URL(entry.name + (entry.isDirectory ? "/" : ""), directory);
    if (entry.isDirectory) yield* walk(child);
    else yield decodeURIComponent(child.pathname);
  }
}
