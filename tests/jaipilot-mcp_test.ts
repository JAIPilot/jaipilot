import assert from "node:assert/strict";

const ROOT = new URL("../", import.meta.url);
const PLUGIN = new URL("plugins/jaipilot/", ROOT);
const MCP_URL = "https://api.jaipilot.com/functions/v1/jaipilot-cloud/mcp";
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

Deno.test("remote source upload requires repository-specific affirmative consent", async () => {
  const remote = await Deno.readTextFile(
    new URL("skills/jaipilot-remote-java/SKILL.md", PLUGIN),
  );
  assert.match(remote, /Before the first upload for each repository/);
  assert.match(remote, /ordinary task request does not count/);
  assert.match(remote, /Before the first upload for this repository/);
  assert.match(remote, /Require affirmative user confirmation/);
  assert.match(remote, /consent for another repository/);
  assert.match(remote, /do not name a loop or\s+scalar variable `path`/);
  assert.match(remote, /Treat any HTTP 2xx response as success/);
  assert.match(remote, /Supabase signed uploads normally return 200/);

  const optimize = await Deno.readTextFile(
    new URL("skills/jaipilot-optimize-java/SKILL.md", PLUGIN),
  );
  assert.equal(optimize.includes("exact candidate is already committed"), false);
  assert.match(optimize, /exact tracked and unignored working-tree/);
});

Deno.test("Java verification workflows prefer remote execution without a laptop advantage", async () => {
  const remote = await Deno.readTextFile(
    new URL("skills/jaipilot-remote-java/SKILL.md", PLUGIN),
  );
  assert.match(remote, /hardware by default whenever the laptop provides no concrete advantage/);
  assert.match(remote, /Keep execution local only when the laptop provides a concrete advantage/);

  for (
    const name of [
      "jaipilot-clean-java",
      "jaipilot-fast-execution",
      "jaipilot-generate-tests",
      "jaipilot-optimize-java",
      "jaipilot-review-diff",
    ]
  ) {
    const skill = await Deno.readTextFile(new URL(`skills/${name}/SKILL.md`, PLUGIN));
    assert.match(skill, /Default[\s\S]+`jaipilot-remote-java`/);
    assert.match(skill, /laptop provides no concrete\s+advantage/);
  }
});

Deno.test("performance workflow uses one exact-capacity workspace and rejects weak evidence", async () => {
  const remote = await Deno.readTextFile(
    new URL("skills/jaipilot-remote-java/SKILL.md", PLUGIN),
  );
  assert.match(remote, /API profile `large` \(4 CPU, 8 GiB\)/);
  assert.match(remote, /same workspace, JDK, command, input, warmup/);
  assert.match(remote, /patch and remote Git\s+delta digests match/);

  const performance = await Deno.readTextFile(
    new URL("skills/jaipilot-clean-java/references/performance.md", PLUGIN),
  );
  assert.match(performance, /initial Git commit is the immutable experiment baseline/);
  assert.match(performance, /median improves by at least\s+10%/);
  assert.match(performance, /at least seven comparable runs/);
  assert.match(performance, /JAIPILOT_MEASUREMENTS_V1/);
  assert.match(performance, /keep those proof sources byte-identical/);
  assert.match(performance, /primaryMetric\.rawData/);
  assert.match(performance, /\(n - 1\) \* 0\.95/);
  assert.match(performance, /do\s+not mix it into the operation-level result/);
  assert.match(performance, /remote edits never\s+replace or synchronize the local patch/);
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
