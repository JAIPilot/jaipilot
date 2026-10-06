import assert from "node:assert/strict";
import { join } from "node:path";
import { Client } from "npm:@modelcontextprotocol/sdk@1.32.0/client/index.js";
import { InMemoryTransport } from "npm:@modelcontextprotocol/sdk@1.32.0/inMemory.js";
import { runCommand, type runWorkflow, type WorkflowResult } from "../cli/agent.ts";
import { git } from "../cli/project.ts";
import { loadBaseline, snapshot, testPaths } from "../mcp/baseline.ts";
import { Jobs, type JobView, type LockInput } from "../mcp/jobs.ts";
import { testingServer } from "../mcp/server.ts";

async function fixture() {
  const root = await Deno.makeTempDir({ prefix: "jaipilot-mcp-test-" });
  const source = "src/main/java/com/acme/Order.java";
  const test = "src/test/java/com/acme/OrderTest.java";
  await Deno.mkdir(join(root, "src/main/java/com/acme"), { recursive: true });
  await Deno.mkdir(join(root, "src/test/java/com/acme"), { recursive: true });
  await Deno.writeTextFile(join(root, source), "package com.acme; public class Order {}\n");
  await Deno.writeTextFile(join(root, ".gitignore"), "target/\n");
  await Deno.writeTextFile(
    join(root, "verify.ts"),
    `
    const source = await Deno.readTextFile(${JSON.stringify(source)});
    if (source.includes("BROKEN")) { console.error("baseline assertion failed"); Deno.exit(1); }
    console.log("baseline tests passed");
  `,
  );
  await git(root, "init", "-q");
  const input: LockInput = {
    scope: { classes: ["com.acme.Order"] },
    test_paths: ["src/test/java"],
    test_command: `"${Deno.execPath()}" run -A verify.ts`,
    timeout_seconds: 10,
    intent: "Extract helper while preserving behavior",
  };
  const run: typeof runWorkflow = async (_root, workflow, scope, _signal, options) => {
    assert.equal(workflow, "lock_behavior");
    assert.match(options!.userRequest!, /Only create or edit tests/);
    assert.match(options!.userRequest!, /Extract helper/);
    await Deno.writeTextFile(join(root, test), "// immutable characterization test\n");
    return {
      workflow,
      scope,
      status: "complete",
      summary: "Tests generated",
      verification: { tests: "model claimed pass" },
      testFailures: [],
      nextActions: [],
      gitStatus: "",
      durationMs: 1,
    };
  };
  const dependencies = { run, token: () => Promise.resolve("test-token") };
  return { root, source, test, input, run, dependencies };
}

async function finished(jobs: Jobs, view: JobView): Promise<JobView> {
  const result = await jobs.status(view.job_id, 30);
  assert.ok(!["running", "cancelling"].includes(result.state), JSON.stringify(result));
  return result;
}

Deno.test("MCP tools validate schemas and prepare before editing through the SDK client", async () => {
  const f = await fixture();
  const commandTimeouts: number[] = [];
  const jobs = new Jobs(f.root, {
    ...f.dependencies,
    run: async (...args) => {
      for (const timeoutSeconds of [2, 7200]) {
        await args[4]!.execute!(args[0], {
          command: "echo preparation",
          purpose: "inspect",
          timeoutSeconds,
        }, args[3]);
      }
      return await f.run(...args);
    },
    execute: (...args) => {
      commandTimeouts.push(Number(args[1].timeoutSeconds));
      return runCommand(...args);
    },
  });
  const server = testingServer(jobs);
  const client = new Client({ name: "coding-agent-test", version: "1" });
  const [a, b] = InMemoryTransport.createLinkedPair();
  await server.connect(a);
  await client.connect(b);
  try {
    assert.match(client.getInstructions()!, /Before editing/);
    const catalog = await client.listTools();
    assert.deepEqual(catalog.tools.map((tool: { name: string }) => tool.name), [
      "lock_behavior",
      "verify_behavior",
      "get_job_status",
      "cancel_job",
    ]);
    for (const tool of catalog.tools) assert.ok(tool.outputSchema);
    const invalid = await client.callTool({
      name: "lock_behavior",
      arguments: { ...f.input, scope: { all: true, paths: [f.source] } },
    });
    assert.equal(invalid.isError, true);
    const extra = await client.callTool({
      name: "lock_behavior",
      arguments: { ...f.input, unexpected: true },
    });
    assert.equal(extra.isError, true);
    const input = { ...f.input };
    Reflect.deleteProperty(input, "timeout_seconds");
    const started = await client.callTool({ name: "lock_behavior", arguments: input });
    const job = started.structuredContent as JobView;
    assert.equal(job.ready_to_edit, false);
    const reply = await client.callTool({
      name: "get_job_status",
      arguments: { job_id: job.job_id, wait_seconds: 30 },
    });
    const ready = reply.structuredContent as JobView;
    assert.equal(ready.state, "completed", JSON.stringify(ready));
    assert.equal(ready.ready_to_edit, true);
    assert.equal(ready.verified, false);
    assert.match(
      (ready.result!.verification as { output: string }).output,
      /baseline tests passed/,
    );
    assert.deepEqual(commandTimeouts, [3600, 7200, 3600]);
    const baseline = await loadBaseline(f.root, ready.result!.baseline_id as string);
    assert.equal(baseline.timeoutSeconds, 3600);
    assert.equal(jobs.cancel(job.job_id).state, "completed");
    const unknown = await client.callTool({
      name: "get_job_status",
      arguments: { job_id: crypto.randomUUID() },
    });
    assert.equal(unknown.isError, true);
  } finally {
    await jobs.stop();
    await client.close();
    await server.close();
    await Deno.remove(f.root, { recursive: true });
  }
});

Deno.test("durable baselines preserve dirty files and detect regressions and changed tests", async () => {
  const f = await fixture();
  const jobs = new Jobs(f.root, f.dependencies);
  let restarted: Jobs | undefined;
  try {
    await git(f.root, "add", ".");
    await git(
      f.root,
      "-c",
      "user.name=Test",
      "-c",
      "user.email=test@example.com",
      "commit",
      "-qm",
      "initial",
    );
    const original = await Deno.readTextFile(join(f.root, f.source));
    await Deno.writeTextFile(join(f.root, f.source), `${original}// pre-existing dirty edit\n`);
    const before = await snapshot(f.root);
    const ready = await finished(jobs, await jobs.lock(f.input));
    assert.equal(ready.ready_to_edit, true, JSON.stringify(ready));
    const id = ready.result!.baseline_id as string;
    const baseline = await loadBaseline(f.root, id);
    assert.equal(baseline.protectedFiles[f.source], before[f.source]);
    assert.equal(baseline.testCommand, f.input.test_command);
    await jobs.stop();
    restarted = new Jobs(f.root, { run: () => Promise.reject(new Error("must not call LLM")) });
    await assert.rejects(() => restarted!.status(ready.job_id), /Job not found/);
    await Deno.writeTextFile(
      join(f.root, f.source),
      `${original}// behavior-preserving refactor\n`,
    );
    const passed = await finished(restarted, await restarted.verify(id));
    assert.equal(passed.verified, true, JSON.stringify(passed));
    assert.deepEqual(passed.result!.changed_production_paths, [f.source]);
    await Deno.writeTextFile(join(f.root, f.source), `${original}// BROKEN\n`);
    const failed = await finished(restarted, await restarted.verify(id));
    assert.equal(failed.state, "blocked");
    assert.equal(failed.verified, false);
    assert.equal((failed.result!.verification as { exitCode: number }).exitCode, 1);
    await Deno.writeTextFile(join(f.root, f.source), original);
    await Deno.writeTextFile(join(f.root, f.test), "// weakened assertion\n");
    const weakened = await finished(restarted, await restarted.verify(id));
    assert.equal(weakened.state, "blocked");
    assert.deepEqual(weakened.result!.changed_test_paths, [f.test]);
    await Deno.writeTextFile(join(f.root, f.test), "// immutable characterization test\n");
    const extraTest = "src/test/java/AddedTest.java";
    await Deno.writeTextFile(join(f.root, extraTest), "// extra test\n");
    const added = await finished(restarted, await restarted.verify(id));
    assert.deepEqual(added.result!.changed_test_paths, [extraTest]);
  } finally {
    await jobs.stop();
    await restarted?.stop();
    await Deno.remove(f.root, { recursive: true });
  }
});

Deno.test("preparation blocks protected edits, failed independent tests and blocked model finish", async () => {
  for (const failure of ["source", "independent", "workflow"] as const) {
    const f = await fixture();
    const jobs = new Jobs(f.root, {
      ...f.dependencies,
      run: async (...args) => {
        const result = await f.run(...args);
        if (failure === "source") await Deno.writeTextFile(join(f.root, f.source), "BROKEN\n");
        return {
          ...result,
          status: failure === "workflow" ? "blocked" : "complete",
        } as WorkflowResult;
      },
    });
    try {
      if (failure === "independent") {
        // An already broken baseline may look complete to the model; the real command must reject it.
        const source = await Deno.readTextFile(join(f.root, f.source));
        await Deno.writeTextFile(join(f.root, f.source), `${source}// BROKEN\n`);
      }
      const blocked = await finished(jobs, await jobs.lock(f.input));
      assert.equal(blocked.state, "blocked", JSON.stringify(blocked));
      assert.equal(blocked.ready_to_edit, false);
      assert.equal(blocked.result!.baseline_id, null);
      if (failure === "source") {
        assert.deepEqual(blocked.result!.changed_protected_paths, [f.source]);
      }
      if (failure === "independent") {
        assert.equal((blocked.result!.verification as { exitCode: number }).exitCode, 1);
      }
    } finally {
      await jobs.stop();
      await Deno.remove(f.root, { recursive: true });
    }
  }
});

Deno.test("same-checkout jobs are serialized across instances and cancellation releases the lock", async () => {
  const f = await fixture();
  let entered!: () => void;
  const waiting = new Promise<void>((resolve) => {
    entered = resolve;
  });
  const jobs = new Jobs(f.root, {
    ...f.dependencies,
    run: (_root, _workflow, _scope, signal) =>
      new Promise((_resolve, reject) => {
        entered();
        signal!.addEventListener("abort", () => reject(new Error("Cancelled")), { once: true });
      }),
  });
  const other = new Jobs(f.root, f.dependencies);
  try {
    const started = await jobs.lock(f.input);
    await waiting;
    await assert.rejects(() => jobs.lock(f.input), /already running/);
    await assert.rejects(() => other.lock(f.input), /already running/);
    assert.equal(jobs.cancel(started.job_id).state, "cancelling");
    const cancelled = await finished(jobs, started);
    assert.equal(cancelled.state, "cancelled");
    assert.equal(cancelled.ready_to_edit, false);
    const next = await finished(other, await other.lock(f.input));
    assert.equal(next.ready_to_edit, true, JSON.stringify(next));
  } finally {
    await jobs.stop();
    await other.stop();
    await Deno.remove(f.root, { recursive: true });
  }
});

Deno.test("test path traversal and symlinks are rejected, ignored build artifacts stay outside hashes", async () => {
  const f = await fixture();
  try {
    await assert.rejects(() => testPaths(f.root, ["../outside"]), /Invalid test path/);
    await assert.rejects(() => testPaths(f.root, ["."]), /Invalid test path/);
    await assert.rejects(() => testPaths(f.root, [".git/hooks"]), /Invalid test path/);
    await assert.rejects(() => loadBaseline(f.root, "../../manifest"), /Invalid baseline/);
    if (Deno.build.os !== "windows") {
      await Deno.symlink(join(f.root, "src/test/java"), join(f.root, "linked-tests"));
      await assert.rejects(() => testPaths(f.root, ["linked-tests/child"]), /symbolic link/);
    }
    await Deno.mkdir(join(f.root, "target"));
    await Deno.writeTextFile(join(f.root, "target", "compiled.class"), "ignored output");
    assert.equal((await snapshot(f.root))["target/compiled.class"], undefined);
  } finally {
    await Deno.remove(f.root, { recursive: true });
  }
});

Deno.test("command cancellation and timeouts terminate descendants, including ones ignoring SIGTERM", async () => {
  const root = await Deno.makeTempDir();
  try {
    await Deno.writeTextFile(
      join(root, "grandchild.ts"),
      `
      if (Deno.build.os !== "windows") Deno.addSignalListener("SIGTERM", () => {});
      await Deno.writeTextFile("pid.txt", String(Deno.pid));
      setInterval(() => {}, 1000);
    `,
    );
    await Deno.writeTextFile(
      join(root, "hang.ts"),
      `
      const child = new Deno.Command(Deno.execPath(), {args: ["run", "-A", "grandchild.ts"]}).spawn();
      await child.status;
    `,
    );
    for (const cancel of [true, false]) {
      const controller = new AbortController();
      const command = runCommand(root, {
        command: `"${Deno.execPath()}" run -A hang.ts`,
        purpose: "test",
        timeoutSeconds: cancel ? 20 : 2,
      }, controller.signal);
      // Install a rejection handler immediately while waiting for the subprocess to start.
      const settled = command.then((value) => ({ value }), (error) => ({ error }));
      const started = Date.now();
      while (!await Deno.stat(join(root, "pid.txt")).catch(() => null)) {
        if (Date.now() - started > 10_000) throw new Error("Child did not start");
        await new Promise((resolve) => setTimeout(resolve, 10));
      }
      const pid = Number(await Deno.readTextFile(join(root, "pid.txt")));
      if (cancel) controller.abort();
      const result = await settled;
      if (cancel) assert.match(String("error" in result && result.error), /Cancelled/);
      else {
        assert.ok("value" in result);
        assert.equal(result.value.exitCode, 124);
        assert.equal(result.value.timedOut, true);
      }
      if (Deno.build.os === "windows") {
        // TerminateProcess can return AccessDenied for an already terminated process whose
        // handle has not been released. Check the live process list instead of killing it again.
        const processes = await new Deno.Command("tasklist", {
          args: ["/FI", `PID eq ${pid}`, "/FO", "CSV", "/NH"],
        }).output();
        assert.equal(processes.success, true);
        assert.doesNotMatch(
          new TextDecoder().decode(processes.stdout),
          new RegExp(`^"[^"\\r\\n]+","${pid}",`, "m"),
          `Cancelled descendant ${pid} is still running`,
        );
      } else if (Deno.build.os === "linux") {
        // A killed child can retain its PID as a zombie until reaped. Check whether it can
        // still execute, rather than whether the kernel still recognizes its PID.
        let state = "";
        const deadline = Date.now() + 2_000;
        do {
          const stat = await Deno.readTextFile(`/proc/${pid}/stat`).catch((error) => {
            if (error instanceof Deno.errors.NotFound) return "";
            throw error;
          });
          state = stat ? stat.slice(stat.lastIndexOf(")") + 2).split(" ")[0] : "";
          if (!state || state === "Z" || state === "X") break;
          await new Promise((resolve) => setTimeout(resolve, 10));
        } while (Date.now() < deadline);
        assert.ok(
          !state || state === "Z" || state === "X",
          `Cancelled descendant ${pid} is still ${state}`,
        );
      } else {
        assert.throws(() => Deno.kill(pid, "SIGTERM"), /No such process|not found|os error 3/i);
      }
      await Deno.remove(join(root, "pid.txt"));
    }
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
