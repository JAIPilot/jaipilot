import assert from "node:assert/strict";
import { join, resolve } from "node:path";
import { Client } from "npm:@modelcontextprotocol/sdk@1.32.0/client/index.js";
import { StdioClientTransport } from "npm:@modelcontextprotocol/sdk@1.32.0/client/stdio.js";
import { createHash } from "node:crypto";
import { git } from "../cli/project.ts";
import { loadBaseline, stateDirectory } from "../mcp/baseline.ts";
import type { JobView } from "../mcp/jobs.ts";

// Opt-in integration test: real Codex session + real managed JAIPilot job. Uses account credits.
// Requirements: compiled CLI, Codex login, JAIPilot login, Maven and a compatible JDK (17 recommended).
const binary = resolve(Deno.args[0] ?? "dist/jaipilot");
await Deno.mkdir("dist", { recursive: true });
const artifacts = await Deno.realPath(
  await Deno.makeTempDir({ dir: "dist", prefix: "codex-mcp-" }),
);
const root = join(artifacts, "repository");
const sourcePath = "src/main/java/com/acme/ShippingCost.java";
await Deno.mkdir(join(root, "src/main/java/com/acme"), { recursive: true });
const original = `package com.acme;

public class ShippingCost {
    public int quote(int weightKg, boolean express) {
        if (weightKg <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        int cost;
        if (weightKg <= 5) {
            cost = 5;
        } else if (weightKg <= 20) {
            cost = 12;
        } else {
            cost = 20;
        }
        return express ? cost + 8 : cost;
    }
}
`;
await Deno.writeTextFile(join(root, sourcePath), original);
await Deno.writeTextFile(join(root, ".gitignore"), "target/\n");
await Deno.writeTextFile(
  join(root, "pom.xml"),
  `<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion><groupId>com.acme</groupId><artifactId>mcp-smoke</artifactId><version>1.0</version>
  <properties><maven.compiler.release>17</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.11.4</version><scope>test</scope></dependency></dependencies>
  <build><plugins>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version></plugin>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version><configuration><failIfNoTests>true</failIfNoTests></configuration></plugin>
    <plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.12</version><executions>
      <execution><goals><goal>prepare-agent</goal></goals></execution>
      <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
    </executions></plugin>
  </plugins></build>
</project>\n`,
);
await git(root, "init", "-q");
await git(root, "add", ".");
await git(
  root,
  "-c",
  "user.name=JAIPilot Test",
  "-c",
  "user.email=test@example.com",
  "commit",
  "-qm",
  "fixture",
);
console.log(`Codex MCP test artifacts: ${artifacts}`);
const child = new Deno.Command("codex", {
  args: [
    "exec",
    "--ephemeral",
    "--ignore-user-config",
    "--json",
    "-s",
    "workspace-write",
    "-c",
    'approval_policy="never"',
    "-c",
    `mcp_servers.jaipilot.command=${JSON.stringify(binary)}`,
    "-c",
    `mcp_servers.jaipilot.args=${JSON.stringify(["mcp", "--repo", root])}`,
    "-c",
    "mcp_servers.jaipilot.required=true",
    "-c",
    'mcp_servers.jaipilot.default_tools_approval_mode="approve"',
    "-C",
    root,
    "-o",
    join(artifacts, "codex-final.txt"),
    "Refactor ShippingCost.quote to use named constants for tier prices and the express surcharge. " +
    "Preserve all observable behavior. Do not commit or push.",
  ],
  stdin: "null",
  stdout: "piped",
  stderr: "piped",
}).spawn();
const eventsPath = join(artifacts, "codex-events.jsonl");
const eventsFile = await Deno.open(eventsPath, { create: true, write: true });
const stderrFile = await Deno.open(join(artifacts, "codex-stderr.txt"), {
  create: true,
  write: true,
});
await Promise.all([
  child.stdout.pipeTo(eventsFile.writable),
  child.stderr.pipeTo(stderrFile.writable),
]);
assert.equal((await child.status).code, 0, `Codex failed; see ${artifacts}`);
const baselines = join(await stateDirectory(root), "baselines");
assert.ok(
  await Deno.stat(baselines).catch(() => null),
  `Codex did not prepare a baseline; see ${artifacts}`,
);
const ids: string[] = [];
for await (const file of Deno.readDir(baselines)) {
  if (file.name.endsWith(".json")) ids.push(file.name.slice(0, -5));
}
assert.equal(ids.length, 1, "Expected one original baseline, without regenerating after edits");
const baseline = await loadBaseline(root, ids[0]);
const originalHash = createHash("sha256").update(original).update(":0").digest("hex");
assert.equal(baseline.protectedFiles[sourcePath], originalHash, "Codex edited before preparation");
assert.equal(baseline.verification.exitCode, 0);
const refactored = await Deno.readTextFile(join(root, sourcePath));
assert.notEqual(refactored, original, "Codex did not perform the requested refactor");
const events = await Deno.readTextFile(eventsPath);
assert.match(events, /lock_behavior/);
assert.match(events, /verify_behavior/);
assert.match(events, /"verified"\s*:\s*true/);
console.log(`Separate Codex session prepared baseline ${baseline.id}, edited and verified.`);

const transport = new StdioClientTransport({
  command: binary,
  args: ["mcp", "--repo", root],
  stderr: "pipe",
});
const client = new Client({ name: "jaipilot-regression-check", version: "1" });
await client.connect(transport);
async function verify(): Promise<JobView> {
  const started = (await client.callTool({
    name: "verify_behavior",
    arguments: { baseline_id: baseline.id },
  })).structuredContent as JobView;
  while (true) {
    const job = (await client.callTool({
      name: "get_job_status",
      arguments: { job_id: started.job_id, wait_seconds: 30 },
    })).structuredContent as JobView;
    if (!["running", "cancelling"].includes(job.state)) return job;
  }
}
try {
  const passed = await verify();
  assert.equal(passed.verified, true, JSON.stringify(passed));
  await Deno.writeTextFile(join(root, sourcePath), original.replace("cost = 5;", "cost = 500;"));
  const regression = await verify();
  assert.equal(regression.state, "blocked", JSON.stringify(regression));
  assert.equal(regression.verified, false);
  assert.notEqual((regression.result!.verification as { exitCode: number }).exitCode, 0);
  await Deno.writeTextFile(
    join(artifacts, "regression-result.json"),
    JSON.stringify(regression, null, 2),
  );
  await Deno.writeTextFile(join(root, sourcePath), refactored);
  const restored = await verify();
  assert.equal(restored.verified, true, JSON.stringify(restored));
  let testCount = 0;
  for await (const file of Deno.readDir(join(root, "target/surefire-reports"))) {
    if (!file.name.startsWith("TEST-") || !file.name.endsWith(".xml")) continue;
    const xml = await Deno.readTextFile(join(root, "target/surefire-reports", file.name));
    testCount += Number(xml.match(/<testsuite[^>]*\btests="(\d+)"/)?.[1] ?? 0);
    assert.match(xml, /<testsuite[^>]*\bfailures="0"/);
    assert.match(xml, /<testsuite[^>]*\berrors="0"/);
  }
  assert.ok(testCount > 0, "No JUnit tests actually ran");
  const xml = await Deno.readTextFile(join(root, "target/site/jacoco/jacoco.xml"));
  const lines = [...xml.matchAll(/<counter type="LINE" missed="(\d+)" covered="(\d+)"/g)].at(-1)!;
  const summary = {
    baseline_id: baseline.id,
    tests: testCount,
    line_coverage: Number(lines[2]) / (Number(lines[1]) + Number(lines[2])) * 100,
    separate_codex: "passed",
    intentional_regression: "detected",
    restored_verification: "passed",
    artifacts,
  };
  await Deno.writeTextFile(join(artifacts, "summary.json"), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
} finally {
  await Deno.writeTextFile(join(root, sourcePath), refactored);
  await client.close();
}
