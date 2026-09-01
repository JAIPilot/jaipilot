import assert from "node:assert/strict";

const ROOT = new URL("../", import.meta.url);
const PLUGIN = new URL("plugins/jaipilot/", ROOT);
const MCP_URL = "https://api.jaipilot.com/functions/v1/jaipilot/mcp";
type McpManifest = {
  mcpServers: Record<string, { type: string; url: string }>;
};

Deno.test("Codex and Claude packages expose the same hosted MCP binding", async () => {
  const claude = await json<McpManifest>(".mcp.json");
  const codex = await json<{
    skills: string;
    mcpServers: string;
    interface: {
      shortDescription: string;
      defaultPrompt: string[];
    };
  }>(".codex-plugin/plugin.json");
  const expected = { type: "http", url: MCP_URL };
  assert.deepEqual(claude.mcpServers, { "jaipilot-remote": expected });
  assert.equal(codex.skills, "./skills/");
  assert.equal(codex.mcpServers, "./.mcp.json");
  assert.equal(codex.interface.shortDescription.length <= 30, true);
  assert.equal(codex.interface.defaultPrompt.length, 3);
  assert.equal(codex.interface.defaultPrompt.every((prompt) => prompt.length <= 128), true);
  await assert.rejects(
    Deno.stat(new URL(".agents/plugins/marketplace.json", ROOT)),
    Deno.errors.NotFound,
  );
  const readme = await Deno.readTextFile(new URL("README.md", ROOT));
  assert.match(readme, new RegExp(`codex mcp add jaipilot --url ${MCP_URL}`));
  assert.match(
    readme,
    /### Any MCP client[\s\S]+"mcpServers"[\s\S]+"type": "http"[\s\S]+`skill_get`/,
  );
  assert.match(
    readme,
    /### See what JAIPilot changed[\s\S]+^\*\*JAIPilot impact\*\*$[\s\S]+`not measured`/m,
  );
  assert.match(
    readme,
    /\*\*JAIPilot · <capability>\*\* — <completed outcome>; <strongest fresh proof>\./,
  );
  assert.match(readme, /official OpenAI plugin directory/);
});

Deno.test("the distributable contains no provider or shared-secret configuration", async () => {
  const files: string[] = [];
  for await (const entry of walk(PLUGIN)) files.push(entry);
  const text = (await Promise.all(files.map((file) => Deno.readTextFile(file)))).join("\n");
  assert.equal(/JAIPILOT_CLOUD_TRIGGER_SECRET|DAYTONA|GITHUB_APP_PRIVATE_KEY/i.test(text), false);
  assert.equal(files.some((file) => file.endsWith(".ts")), false);
});

Deno.test("remote source upload requires consent and an exact committed archive", async () => {
  const remote = await Deno.readTextFile(
    new URL("skills/jaipilot-remote-java/SKILL.md", PLUGIN),
  );
  assert.match(remote, /Before the first upload for each repository/);
  assert.match(remote, /ordinary coding\s+request/);
  assert.match(remote, /affirmative repository-specific confirmation/);
  assert.match(remote, /consent for another repository/);
  assert.match(remote, /Never upload staged, unstaged, untracked, ignored/);
  assert.match(remote, /`git archive --format=zip`/);
  assert.match(remote, /Treat any HTTP 2xx response as success/);
  assert.match(remote, /supplying every returned `upload_headers` entry exactly/);

  const optimize = await Deno.readTextFile(
    new URL("skills/jaipilot-optimize-java/SKILL.md", PLUGIN),
  );
  assert.match(optimize, /remote proof covers only the uploaded exact commit/);
  assert.match(optimize, /authorized commit exists/);
});

Deno.test("Java verification workflows prefer remote execution without a laptop advantage", async () => {
  const remote = await Deno.readTextFile(
    new URL("skills/jaipilot-remote-java/SKILL.md", PLUGIN),
  );
  assert.match(remote, /Prefer remote execution[\s\S]+laptop has no\s+concrete advantage/);
  assert.match(remote, /Keep execution local when it needs the laptop's VPN/);

  for (
    const name of [
      "jaipilot-clean-java",
      "jaipilot-fast-execution",
      "jaipilot-generate-tests",
      "jaipilot-openrewrite",
      "jaipilot-optimize-java",
      "jaipilot-review-diff",
    ]
  ) {
    const skill = await Deno.readTextFile(new URL(`skills/${name}/SKILL.md`, PLUGIN));
    assert.match(skill, /Default[\s\S]+`jaipilot-remote-java`/);
    assert.match(skill, /laptop provides no concrete\s+advantage/);
  }
});

Deno.test("all JAIPilot workflows route substantial command work through safe parallelism", async () => {
  const fast = await Deno.readTextFile(
    new URL("skills/jaipilot-fast-execution/SKILL.md", PLUGIN),
  );
  assert.match(fast, /Every JAIPilot workflow should use this skill for substantial command work/);
  assert.match(fast, /bounded native parallelism can reduce total wall time/);
  assert.match(fast, /not permission to assume independence or\s+create contention/);

  for (
    const name of [
      "jaipilot-clean-java",
      "jaipilot-generate-tests",
      "jaipilot-maintainer-intent",
      "jaipilot-openrewrite",
      "jaipilot-optimize-java",
      "jaipilot-remote-java",
      "jaipilot-review-diff",
    ]
  ) {
    const skill = await Deno.readTextFile(new URL(`skills/${name}/SKILL.md`, PLUGIN));
    assert.match(skill, /`jaipilot-fast-execution`/);
    assert.match(
      skill,
      /safe\s+batching\s+or\s+bounded\s+native\s+parallelism\s+can\s+reduce\s+wall\s+time/,
    );
    assert.match(skill, /without\s+changing\s+the\s+required\s+proof/);
  }
});

Deno.test("every skill surfaces one evidence-backed JAIPilot impact handoff", async () => {
  const milestones = new Map([
    [
      "jaipilot-clean-java",
      "`**JAIPilot · Java cleanup** — <completed outcome>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-fast-execution",
      "`**JAIPilot · Execution** — <completed outcome>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-generate-tests",
      "`**JAIPilot · Test generation** — <completed outcome>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-maintainer-intent",
      "`**JAIPilot · Maintainer intent** — <completed decision>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-openrewrite",
      "`**JAIPilot · Migration** — <completed outcome>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-optimize-java",
      "`**JAIPilot · Optimization** — <completed outcome>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-remote-java",
      "`**JAIPilot · Remote verification** — <completed outcome>; <strongest fresh proof>.`",
    ],
    [
      "jaipilot-review-diff",
      "`**JAIPilot · Diff review** — <completed outcome>; <strongest fresh proof>.`",
    ],
  ]);

  for (const [name, milestone] of milestones) {
    const root = new URL(`skills/${name}/`, PLUGIN);
    const skill = await Deno.readTextFile(new URL("SKILL.md", root));
    const impact = await Deno.readTextFile(new URL("references/impact-reporting.md", root));
    assert.match(skill, /\[impact-reporting\.md\]\(references\/impact-reporting\.md\)/);
    const skillMilestone = milestone.replace("<completed outcome>", "<outcome>")
      .replace("<completed decision>", "<decision>")
      .replace("<strongest fresh proof>", "<proof>");
    assert.equal(skill.includes(skillMilestone), true);
    assert.match(skill, /Announce a completed (?:result|decision) only as/);
    assert.match(
      skill,
      /in\s+progress or as the final outcome lead\.\s+Then\s+render this exact flat section; do not nest bullets:/,
    );
    assert.equal([...skill.matchAll(/^\*\*JAIPilot impact\*\*$/gm)].length, 1);
    const label = milestone.match(/JAIPilot · ([^*]+)\*\*/)?.[1];
    assert.ok(label);
    assert.match(skill, new RegExp(`^- \\*\\*${label}:\\*\\*`, "m"));
    assert.equal([...skill.matchAll(/^- \*\*Evidence:\*\*/gm)].length, 1);
    assert.match(
      skill,
      new RegExp(
        `\\*\\*JAIPilot impact\\*\\*\\n- \\*\\*${label}:\\*\\* <(?:outcome|decision)>\\n- \\*\\*Evidence:\\*\\* <strongest proof>`,
      ),
    );
    assert.equal(
      [...impact.matchAll(/^\*\*JAIPilot impact\*\*$/gm)].length,
      1,
      `${name} must define exactly one final impact card`,
    );
    assert.match(impact, /Immediately after the final response's one-sentence outcome lead/);
    assert.match(impact, /coordinat(?:ing|or)/);
    assert.match(impact, /Do not upload or\s+persist\s+impact telemetry/);
    assert.equal(
      [...impact.matchAll(/^`\*\*JAIPilot · .+\*\* — .+; .+\.`$/gm)].length,
      1,
      `${name} must define exactly one consistently branded milestone grammar`,
    );
    assert.equal(impact.includes(milestone), true);
    assert.match(impact, /Use completed evidence, not\s+intent or routing/);
    assert.match(
      impact,
      /When\s+completion\s+coincides with the final response, use this line as its outcome lead immediately\s+before\s+the\s+impact\s+card/,
    );
    assert.match(impact, /This is the only format for announcing a completed milestone/);
    assert.match(impact, /The card is not a closing appendix/);
    assert.match(impact, /preserve the exact flat heading and bullet structure/);
    assert.match(impact, /Do not\s+nest bullets/);
    assert.match(impact, /Use exactly these two flat rows/);
    assert.equal([...impact.matchAll(/^- \*\*Evidence:\*\*/gm)].length, 1);
    assert.match(
      impact,
      new RegExp(
        `\\*\\*JAIPilot impact\\*\\*\\n- \\*\\*${label}:\\*\\*[^\\n]+\\n- \\*\\*Evidence:\\*\\*[^\\n]+`,
      ),
    );
  }

  const tests = await Deno.readTextFile(
    new URL("skills/jaipilot-generate-tests/references/impact-reporting.md", PLUGIN),
  );
  assert.match(tests, /percentage points/);
  assert.match(tests, /never abbreviate them\s+as `pp`/);
  assert.match(tests, /`coverage delta not measured`/);

  for (const name of ["jaipilot-fast-execution", "jaipilot-remote-java"]) {
    const impact = await Deno.readTextFile(
      new URL(`skills/${name}/references/impact-reporting.md`, PLUGIN),
    );
    assert.match(impact, /matched baseline/);
    assert.match(impact, /`speedup not measured`/);
  }

  const optimize = await Deno.readTextFile(
    new URL("skills/jaipilot-optimize-java/references/impact-reporting.md", PLUGIN),
  );
  assert.match(optimize, /merely loading a skill does not earn attribution/);
  assert.match(optimize, /same revisions, prompt, model, tools, budget, and acceptance tests/);
});

Deno.test("test generation processes and executes every safely independent scoped class in parallel", async () => {
  const tests = await Deno.readTextFile(
    new URL("skills/jaipilot-generate-tests/SKILL.md", PLUGIN),
  );
  const generalPlan = tests.indexOf("## Parallelize the complete scoped class queue");
  const coverageCampaign = tests.indexOf("## Run a coverage campaign when requested");
  assert.equal(generalPlan > 0 && generalPlan < coverageCampaign, true);
  assert.match(tests, /Apply this plan to every request that contains multiple test classes/);
  assert.match(tests, /Every\s+scoped class must receive useful tests or a reported reason/);
  assert.match(tests, /execute every safely independent changed test class in parallel/);
  assert.match(tests, /repository-configured JUnit, TestNG, Surefire, Failsafe, Gradle/);
  assert.match(tests, /isolated worktrees and output directories/);
  assert.match(tests, /Never run concurrent Maven or Gradle processes against one checkout/);
  assert.match(tests, /fails only in parallel[\s\S]+serially once/);
  assert.match(
    tests,
    /workers or a safe parallel mechanism are unavailable[\s\S]+state that limitation/,
  );
});

Deno.test("OpenRewrite migrations stay pinned, previewed, reviewable, and verified", async () => {
  const rewrite = await Deno.readTextFile(
    new URL("skills/jaipilot-openrewrite/SKILL.md", PLUGIN),
  );
  const running = await Deno.readTextFile(
    new URL("skills/jaipilot-openrewrite/references/running-recipes.md", PLUGIN),
  );

  assert.match(
    rewrite,
    /`CONFIGURED_RECIPE`[\s\S]+`TEMPORARY_RECIPE`[\s\S]+`MANUAL`[\s\S]+`NO_ACTION`/,
  );
  assert.match(rewrite, /Never use `latest`, `latest\.release`, snapshots,\s+dynamic ranges/);
  assert.match(rewrite, /obtain approval before\s+adding or running a plugin, recipe dependency/);
  assert.match(rewrite, /dry run before any source-writing run/);
  assert.match(rewrite, /Inspect every proposed file and hunk/);
  assert.match(rewrite, /Re-run the same dry run after the candidate stabilizes/);
  assert.match(
    rewrite,
    /never treat files created\s+inside a disposable remote build as the patch/,
  );
  assert.match(running, /rewrite:dryRun/);
  assert.match(running, /rewriteDryRun/);
  assert.match(running, /failOnInvalidActiveRecipes=true/);
  assert.match(running, /second cycle that produces no further change/);
});

Deno.test("maintainer intent fails closed before upstream implementation", async () => {
  const history = await Deno.readTextFile(
    new URL("skills/jaipilot-maintainer-intent/SKILL.md", PLUGIN),
  );
  assert.match(history, /`CONTRIBUTING\.md`/);
  assert.match(history, /applicable pull-request templates/);
  assert.match(history, /AI-assisted contribution\s+or disclosure policy/);
  assert.match(history, /If a document is absent or inaccessible, report\s+that fact/);
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
  assert.match(knowledge, /\d+ companion attempts covering \d+ source upgrades/);
  assert.match(knowledge, /Performance, cleanup, refactoring[\s\S]+do not belong here/);
  assert.match(knowledge, /Never add private repositories, customer source, credentials/);

  const campaign = knowledge.match(/(\d+) companion attempts covering (\d+) source upgrades/);
  assert.ok(campaign);
  const records = [...knowledge.matchAll(/^## (U-\d{3}) — /gm)].map((match) => match[1]);
  assert.equal(records.length, Number(campaign[1]));
  assert.deepEqual(records, [...records].sort());
  assert.equal(new Set(records).size, records.length);

  for (const section of knowledge.split(/^## U-\d{3} — /m).slice(1)) {
    for (
      const field of [
        "**Source upgrade:**",
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
    assert.equal(
      section.includes("**Companion:**") || section.includes("**Replacement:**"),
      true,
      "knowledge record is missing companion or replacement identity",
    );
    assert.match(section, /`[0-9a-f]{40}`/);
  }

  const indexRows = [...knowledge.matchAll(
    /^\| (U-\d{3}) \| \[[^\]]+\]\((https:\/\/github\.com\/[^)]+\/pull\/\d+)\) \| \[#\d+\]\((https:\/\/github\.com\/[^)]+\/pull\/\d+)\) \| (Accepted|Maintainer-directed|Candidate|Unadopted) \|/gm,
  )];
  assert.deepEqual(indexRows.map((row) => row[1]), records);
  assert.equal(new Set(indexRows.map((row) => row[2])).size, Number(campaign[1]));
  assert.equal(new Set(indexRows.map((row) => row[3])).size, Number(campaign[2]));
  assert.deepEqual(
    [...new Set(indexRows.map((row) => row[4]))].sort(),
    ["Accepted", "Candidate", "Maintainer-directed", "Unadopted"],
  );
  assert.match(
    knowledge,
    /before every campaign handoff, compare this index with all\s+public `skrcode` pull requests/i,
  );
  assert.match(
    knowledge,
    /a green\s+companion, a merged companion, and an end-to-end accepted source upgrade are distinct states/i,
  );

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

Deno.test("performance workflow compares exact commits on matched disposable builds", async () => {
  const remote = await Deno.readTextFile(
    new URL("skills/jaipilot-remote-java/SKILL.md", PLUGIN),
  );
  assert.match(remote, /`large` is 4 CPU\/8 GiB/);
  assert.match(remote, /same CodeBuild\s+profile, JDK, image, command, input/);
  assert.match(remote, /at least seven\s+observations inside one build/);

  const performance = await Deno.readTextFile(
    new URL("skills/jaipilot-clean-java/references/performance.md", PLUGIN),
  );
  assert.match(performance, /exact baseline commit/);
  assert.match(performance, /median\s+improves by at least\s+10%/);
  assert.match(performance, /at least seven comparable observations/);
  assert.match(performance, /keep those proof sources byte-identical/);
  assert.match(performance, /primaryMetric\.rawData/);
  assert.match(performance, /\(n - 1\) \* 0\.95/);
  assert.match(performance, /do\s+not\s+mix\s+it\s+into\s+the\s+operation-level\s+result/);
  assert.match(performance, /Never upload staged, unstaged, or untracked candidate files/);
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
