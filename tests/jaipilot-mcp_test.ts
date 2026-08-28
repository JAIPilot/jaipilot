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

Deno.test("maintainer intent fails closed before upstream implementation", async () => {
  const history = await Deno.readTextFile(
    new URL("skills/jaipilot-maintainer-intent/SKILL.md", PLUGIN),
  );
  assert.match(history, /read the complete conversation/i);
  assert.match(history, /open and closed issues and pull requests/i);
  assert.match(history, /git log`, `git blame`, and the introducing commit/);
  assert.match(
    history,
    /direct default-branch pull request versus a patch onto an\s+existing bot branch/,
  );
  assert.match(
    history,
    /`PROCEED`[\s\S]+`JOIN_EXISTING`[\s\S]+`COMMENT`[\s\S]+`WAIT`[\s\S]+`NO_ACTION`/,
  );
  assert.match(history, /Any unresolved blocker prevents `PROCEED`/);
  assert.match(history, /do not enumerate unrelated repository history/);
  assert.match(
    history,
    /does not authorize a comment, issue, branch, commit, push, or pull\s+request/,
  );
  assert.match(
    history,
    /use it only for a dependency or version\s+upgrade/,
  );
  assert.match(history, /For unrelated maintenance, report it as out of scope and ignore it/);
  assert.match(
    history,
    /`Accepted` records as precedent,[\s\S]+`Candidate` records only as diagnostic\s+hypotheses,[\s\S]+`Unadopted` records as warnings/,
  );
  assert.match(history, /choose the delivery channel and one\s+final decision/);

  const optimize = await Deno.readTextFile(
    new URL("skills/jaipilot-optimize-java/SKILL.md", PLUGIN),
  );
  assert.match(optimize, /invoke `jaipilot-maintainer-intent` before editing/);
  assert.match(optimize, /Continue only when its decision\s+is `PROCEED`/);
});

Deno.test("the optional cross-repository record covers the dependency-upgrade campaign", async () => {
  const knowledge = await Deno.readTextFile(
    new URL("CROSS_REPO_KNOWLEDGE.md", ROOT),
  );

  assert.match(knowledge, /optional input, not product state/i);
  assert.match(knowledge, /version-upgrade work only/i);
  assert.match(knowledge, /30 companion attempts covering 26 source upgrades/);
  assert.match(knowledge, /Performance, cleanup, refactoring[\s\S]+do not belong here/);
  assert.match(knowledge, /Never add private repositories, customer source, credentials/);

  const expected = Array.from(
    { length: 30 },
    (_, index) => `U-${String(index + 1).padStart(3, "0")}`,
  );
  const records = [...knowledge.matchAll(/^## (U-\d{3}) — /gm)].map((match) => match[1]);
  assert.deepEqual(records, expected);
  assert.equal(new Set(records).size, records.length);

  for (const section of knowledge.split(/^## U-\d{3} — /m).slice(1)) {
    for (
      const field of [
        "**Source upgrade:**",
        "**Companion:**",
        "**Failure and candidate:**",
        "**Outcome:**",
        "**Transfer:**",
      ]
    ) {
      assert.equal(
        section.includes(field),
        true,
        `knowledge record is missing ${field}`,
      );
    }
    assert.match(section, /`[0-9a-f]{40}`/);
  }

  const indexRows = [...knowledge.matchAll(
    /^\| (U-\d{3}) \| \[[^\]]+\]\((https:\/\/github\.com\/[^)]+\/pull\/\d+)\) \| \[#\d+\]\((https:\/\/github\.com\/[^)]+\/pull\/\d+)\) \| (Accepted|Maintainer-directed|Candidate|Unadopted) \|/gm,
  )];
  assert.deepEqual(indexRows.map((row) => row[1]), expected);
  assert.equal(new Set(indexRows.map((row) => row[2])).size, 30);
  assert.equal(new Set(indexRows.map((row) => row[3])).size, 26);
  assert.equal(indexRows.filter((row) => row[4] === "Accepted").length, 2);
  assert.equal(indexRows.filter((row) => row[4] === "Maintainer-directed").length, 6);
  assert.equal(indexRows.filter((row) => row[4] === "Candidate").length, 13);
  assert.equal(indexRows.filter((row) => row[4] === "Unadopted").length, 9);

  const revisions = [...knowledge.matchAll(/`([0-9a-f]{40})`/g)];
  assert.equal(revisions.length >= 50, true);
  assert.equal(/https?:\/\/(?!github\.com\/)/.test(knowledge), false);
});

Deno.test("collection-view bypasses require exception-timing characterization", async () => {
  for (
    const name of [
      "jaipilot-generate-tests",
      "jaipilot-optimize-java",
      "jaipilot-review-diff",
    ]
  ) {
    const skill = await Deno.readTextFile(new URL(`skills/${name}/SKILL.md`, PLUGIN));
    assert.match(skill, /public accessor or\s+derived view/);
    assert.match(skill, /copy, sort, validation, and exception timing/);
    assert.match(skill, /malformed later element after an earlier match/);
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
