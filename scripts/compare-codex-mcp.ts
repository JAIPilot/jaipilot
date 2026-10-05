import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { join, resolve } from "node:path";
import { bearerToken } from "../cli/auth.ts";
import { runCommand } from "../cli/agent.ts";
import { git } from "../cli/project.ts";
import { loadBaseline, stateDirectory } from "../mcp/baseline.ts";

// Opt-in paired comparison. Runs real Codex and JAIPilot, spending account credits.
// Inputs and faults are fixed before either run; no selection based on the result.
const commit = "99b940733a9f6bc409457dba7108f08421d81e42";
const names = ["Deadline", "LockUtils"];
const packagePath = "org/apache/kafka/server/util";
const model = "gpt-6.1-sol", effort = "xhigh";
const binary = resolve(Deno.args[0] ?? "dist/jaipilot");
await bearerToken(); // Check/refresh login without printing credentials before either timed run.
await Deno.mkdir("dist", { recursive: true });
const artifacts = await Deno.realPath(
  await Deno.makeTempDir({ dir: "dist", prefix: "codex-comparison-" }),
);
console.log(`Comparison artifacts: ${artifacts}`);
const originals: Record<string, string> = {};
for (const name of names) {
  const url =
    `https://raw.githubusercontent.com/apache/kafka/${commit}/server-common/src/main/java/${packagePath}/${name}.java`;
  const response = await fetch(url);
  assert.equal(response.ok, true, url);
  originals[name] = await response.text();
}
const faults = [
  { id: "negative-delay-accepted", name: "Deadline", from: "delay < 0", to: "delay < -1" },
  {
    id: "time-unit-ignored",
    name: "Deadline",
    from: "timeUnit.toNanos(delay)",
    to: "delay",
  },
  {
    id: "delay-subtracted",
    name: "Deadline",
    from: "add(BigInteger.valueOf",
    to: "subtract(BigInteger.valueOf",
  },
  {
    id: "overflow-clamped-to-zero",
    name: "Deadline",
    from: "new Deadline(Long.MAX_VALUE)",
    to: "new Deadline(0)",
  },
  {
    id: "equality-inverted",
    name: "Deadline",
    from: "nanoseconds == other.nanoseconds",
    to: "nanoseconds != other.nanoseconds",
  },
  {
    id: "supplier-result-lost",
    name: "LockUtils",
    from: "return supplier.get();",
    to: "supplier.get(); return null;",
  },
  {
    id: "supplier-lock-not-released",
    name: "LockUtils",
    from: "lock.unlock();",
    to: "/* unlock omitted */",
  },
  {
    id: "runnable-lock-not-released",
    name: "LockUtils",
    from: "lock.unlock();",
    to: "/* unlock omitted */",
    last: true,
  },
  {
    id: "read-lock-becomes-write-lock",
    name: "LockUtils",
    from: "return inLock(lock.readLock(), supplier);",
    to: "return inLock(lock.writeLock(), supplier);",
  },
  {
    id: "runnable-never-called",
    name: "LockUtils",
    from: "runnable.run();",
    to: "/* callback omitted */",
  },
];
for (const fault of faults) assert.ok(originals[fault.name].includes(fault.from), fault.id);
const pom = `<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion><groupId>com.acme</groupId><artifactId>kafka-utilities-comparison</artifactId><version>1.0</version>
  <properties><maven.compiler.release>17</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <dependencies>
    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.9.1</version></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.11.4</version><scope>test</scope></dependency>
  </dependencies>
  <build><plugins>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version></plugin>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version><configuration><failIfNoTests>true</failIfNoTests></configuration></plugin>
    <plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.12</version><executions>
      <execution><goals><goal>prepare-agent</goal></goals></execution>
      <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
    </executions></plugin>
  </plugins></build>
</project>\n`;
const prompt =
  "Refactor Deadline.fromDelay by extracting its BigInteger deadline calculation and " +
  "saturation into a private static helper. Preserve all observable behavior and existing tests. " +
  "Do not change pom.xml, other production classes, or commit or push. " +
  "Do not inspect sibling checkouts or their results.";
const existingTests = `package org.apache.kafka.server.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;

class ExistingBehaviorTest {
    @Test void directDeadlineKeepsItsValue() {
        assertEquals(42L, Deadline.fromMonotonicNanoseconds(42L).nanoseconds());
    }
    @Test void sameDeadlinesAreEqual() {
        assertEquals(Deadline.fromMonotonicNanoseconds(42L), Deadline.fromMonotonicNanoseconds(42L));
    }
    @Test void runnableRunsAndReleasesLock() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        AtomicBoolean called = new AtomicBoolean();
        LockUtils.inLock(lock, (LockUtils.ThrowingRunnable<Exception>) () -> called.set(true));
        assertTrue(called.get());
        assertFalse(lock.isLocked());
    }
}
`;
const roots: Record<string, string> = {};
for (const mode of ["codex-alone", "codex-with-jaipilot"]) {
  const root = roots[mode] = join(artifacts, mode);
  await Deno.mkdir(join(root, "src/main/java", packagePath), { recursive: true });
  await Deno.mkdir(join(root, "src/test/java"), { recursive: true });
  await Deno.mkdir(join(root, "src/test/java", packagePath), { recursive: true });
  await Deno.writeTextFile(
    join(root, "src/test/java", packagePath, "ExistingBehaviorTest.java"),
    existingTests,
  );
  await Deno.writeTextFile(join(root, ".gitignore"), "target/\n");
  await Deno.writeTextFile(join(root, "pom.xml"), pom);
  for (const name of names) {
    await Deno.writeTextFile(
      join(root, "src/main/java", packagePath, `${name}.java`),
      originals[name],
    );
  }
  for (const file of ["LICENSE", "NOTICE"]) {
    const response = await fetch(
      `https://raw.githubusercontent.com/apache/kafka/${commit}/${file}`,
    );
    assert.equal(response.ok, true);
    await Deno.writeTextFile(join(root, file), await response.text());
  }
  // Warm identical compiler, dependency, test and coverage caches before timing either agent.
  const warmup = await runCommand(root, {
    command: "mvn -q clean verify",
    timeoutSeconds: 600,
    purpose: "comparison setup",
  });
  await Deno.writeTextFile(join(artifacts, `${mode}-setup.txt`), warmup.output);
  assert.equal(warmup.exitCode, 0, warmup.output);
  await Deno.copyFile(
    join(root, "target/site/jacoco/jacoco.xml"),
    join(artifacts, `${mode}-initial-coverage.xml`),
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
}

const runs: Record<string, unknown>[] = [];
for (const mode of ["codex-alone", "codex-with-jaipilot"]) {
  const root = roots[mode];
  console.log(`Starting ${mode}: ${model}, ${effort}`);
  const mcpArgs = mode === "codex-with-jaipilot"
    ? [
      "-c",
      `mcp_servers.jaipilot.command=${JSON.stringify(binary)}`,
      "-c",
      `mcp_servers.jaipilot.args=${JSON.stringify(["mcp", "--repo", root])}`,
      "-c",
      "mcp_servers.jaipilot.required=true",
      "-c",
      'mcp_servers.jaipilot.default_tools_approval_mode="approve"',
    ]
    : [];
  const start = performance.now();
  const child = new Deno.Command("codex", {
    args: [
      "exec",
      "--ephemeral",
      "--ignore-user-config",
      "--json",
      "-m",
      model,
      "-c",
      `model_reasoning_effort=${JSON.stringify(effort)}`,
      "-s",
      "workspace-write",
      "-c",
      'approval_policy="never"',
      ...mcpArgs,
      "-C",
      root,
      "-o",
      join(artifacts, `${mode}-final.txt`),
      prompt,
    ],
    stdin: "null",
    stdout: "piped",
    stderr: "piped",
  }).spawn();
  const eventsPath = join(artifacts, `${mode}-events.jsonl`);
  const eventsFile = await Deno.open(eventsPath, { create: true, write: true });
  const stderrFile = await Deno.open(join(artifacts, `${mode}-stderr.txt`), {
    create: true,
    write: true,
  });
  await Promise.all([
    child.stdout.pipeTo(eventsFile.writable),
    child.stderr.pipeTo(stderrFile.writable),
  ]);
  const durationSeconds = (performance.now() - start) / 1000;
  assert.equal((await child.status).code, 0, `Agent failed; inspect ${artifacts}`);
  const sourcePath = join(root, "src/main/java", packagePath, "Deadline.java");
  const refactored = await Deno.readTextFile(sourcePath);
  assert.notEqual(refactored, originals.Deadline, "Refactor was not performed");
  assert.match(refactored, /private\s+static\s+\w+\s+\w+\s*\(/, "Helper was not extracted");
  assert.equal(await Deno.readTextFile(join(root, "pom.xml")), pom);
  assert.equal(
    await Deno.readTextFile(join(root, "src/test/java", packagePath, "ExistingBehaviorTest.java")),
    existingTests,
  );
  assert.equal(
    await Deno.readTextFile(join(root, "src/main/java", packagePath, "LockUtils.java")),
    originals.LockUtils,
  );
  const events = await Deno.readTextFile(eventsPath);
  let baselineId: string | undefined;
  if (mode === "codex-with-jaipilot") {
    const ids: string[] = [];
    for await (const file of Deno.readDir(join(await stateDirectory(root), "baselines"))) {
      if (file.name.endsWith(".json")) ids.push(file.name.slice(0, -5));
    }
    assert.equal(ids.length, 1, "The original baseline must be preserved");
    const baseline = await loadBaseline(root, ids[0]);
    for (const name of names) {
      assert.equal(
        baseline.protectedFiles[`src/main/java/${packagePath}/${name}.java`],
        createHash("sha256").update(originals[name]).update(":0").digest("hex"),
        "Production changed before baseline preparation",
      );
    }
    assert.match(events, /"verified"\s*:\s*true/);
    baselineId = baseline.id;
  }
  const execute = (purpose: string) =>
    runCommand(root, {
      command: "mvn -q clean verify",
      timeoutSeconds: 180,
      purpose,
    });
  const verified = await execute("independent comparison verification");
  assert.equal(verified.exitCode, 0, verified.output);
  let tests = 0;
  for await (const file of Deno.readDir(join(root, "target/surefire-reports"))) {
    if (!file.name.startsWith("TEST-") || !file.name.endsWith(".xml")) continue;
    const xml = await Deno.readTextFile(join(root, "target/surefire-reports", file.name));
    tests += Number(xml.match(/<testsuite[^>]*\btests="(\d+)"/)?.[1]);
    assert.match(xml, /<testsuite[^>]*\bfailures="0"/);
    assert.match(xml, /<testsuite[^>]*\berrors="0"/);
  }
  assert.ok(tests > 0);
  let coverageXml = "";
  const coverage = (type: string) => {
    const counter = [
      ...coverageXml.matchAll(
        new RegExp(`<counter type="${type}" missed="(\\d+)" covered="(\\d+)"`, "g"),
      ),
    ].at(-1)!;
    return { missed: Number(counter[1]), covered: Number(counter[2]) };
  };
  const mutationResults = [];
  try {
    // Both suites run against the same original implementation with one predefined fault at a time.
    await Deno.writeTextFile(sourcePath, originals.Deadline);
    const originalPass = await execute("original baseline confirmation");
    assert.equal(originalPass.exitCode, 0, originalPass.output);
    coverageXml = await Deno.readTextFile(join(root, "target/site/jacoco/jacoco.xml"));
    await Deno.writeTextFile(join(artifacts, `${mode}-coverage.xml`), coverageXml);
    for (const fault of faults) {
      const path = join(root, "src/main/java", packagePath, `${fault.name}.java`);
      const original = originals[fault.name];
      const index = fault.last ? original.lastIndexOf(fault.from) : original.indexOf(fault.from);
      const mutated = original.slice(0, index) + fault.to +
        original.slice(index + fault.from.length);
      await Deno.writeTextFile(path, mutated);
      const result = await execute(`fault check: ${fault.id}`);
      const detected = result.exitCode !== 0 && !result.timedOut &&
        /Tests run:.*Failures: [1-9]|Failures:\s*\n/.test(result.output);
      assert.ok(
        result.exitCode === 0 || detected,
        `Fault did not compile or execute validly: ${fault.id}\n${result.output}`,
      );
      mutationResults.push({ id: fault.id, detected });
      await Deno.writeTextFile(join(artifacts, `${mode}-${fault.id}.txt`), result.output);
      await Deno.writeTextFile(path, original);
    }
  } finally {
    await Deno.writeTextFile(sourcePath, refactored);
    await Deno.writeTextFile(
      join(root, "src/main/java", packagePath, "LockUtils.java"),
      originals.LockUtils,
    );
  }
  const restored = await execute("restore comparison refactor");
  assert.equal(restored.exitCode, 0, restored.output);
  const usage = events.split("\n").filter(Boolean).map((line) => JSON.parse(line)).findLast((
    event,
  ) => event.type === "turn.completed")?.usage;
  runs.push({
    mode,
    duration_seconds: durationSeconds,
    tests,
    lines: coverage("LINE"),
    branches: coverage("BRANCH"),
    mutation_results: mutationResults,
    baseline_id: baselineId,
    codex_usage: usage,
  });
  await Deno.writeTextFile(
    join(artifacts, "results.json"),
    JSON.stringify({ commit, model, effort, prompt, faults, runs }, null, 2),
  );
  console.log(JSON.stringify(runs.at(-1), null, 2));
}
console.log(`Comparison complete: ${join(artifacts, "results.json")}`);
