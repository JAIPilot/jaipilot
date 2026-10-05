import { join } from "node:path";
import { bearerToken } from "../cli/auth.ts";
import { runCommand, runWorkflow } from "../cli/agent.ts";
import { resolveScope } from "../cli/project.ts";
import {
  type Baseline,
  changed,
  loadBaseline,
  saveBaseline,
  select,
  snapshot,
  stateDirectory,
  testPaths,
  under,
} from "./baseline.ts";

export type LockInput = {
  scope: { all?: boolean; paths?: string[]; classes?: string[]; selections?: string[] };
  test_paths: string[];
  test_command: string;
  timeout_seconds: number;
  intent?: string;
};
export type JobView = {
  job_id: string;
  kind: "lock_behavior" | "verify_behavior";
  state: "running" | "cancelling" | "completed" | "blocked" | "failed" | "cancelled";
  ready_to_edit: boolean;
  verified: boolean;
  created_at: string;
  duration_ms: number;
  progress: string[];
  result?: Record<string, unknown>;
  error?: string;
};
type Job = {
  view: JobView;
  controller: AbortController;
  task: Promise<void>;
};
type Outcome = { state: "completed" | "blocked"; result: Record<string, unknown> };

export class Jobs {
  private jobs = new Map<string, Job>();
  private busy = false;
  private stopped = false;

  constructor(
    readonly root: string,
    private dependencies: {
      run?: typeof runWorkflow;
      execute?: typeof runCommand;
      token?: typeof bearerToken;
    } = {},
  ) {}

  private async acquire(): Promise<() => Promise<void>> {
    const directory = await stateDirectory(this.root);
    await Deno.mkdir(directory, { recursive: true, mode: 0o700 });
    const path = join(directory, "job.lock");
    const file = await Deno.open(path, { create: true, read: true, write: true, mode: 0o600 });
    try {
      // Kernel locks are released on process exit, including crashes. Keep the inode in place.
      if (!await file.tryLock(true)) {
        throw new Error("A JAIPilot MCP job is already running in this repository");
      }
      return async () => {
        try {
          await file.unlock();
        } finally {
          file.close();
        }
      };
    } catch (error) {
      file.close();
      throw error;
    }
  }

  private async start(
    kind: JobView["kind"],
    action: (signal: AbortSignal, report: (message: string) => void) => Promise<Outcome>,
  ): Promise<JobView> {
    if (this.stopped) throw new Error("JAIPilot MCP is stopping");
    if (this.busy) throw new Error("A JAIPilot MCP job is already running in this repository");
    this.busy = true;
    const id = crypto.randomUUID();
    let release: () => Promise<void>;
    try {
      release = await this.acquire();
    } catch (error) {
      this.busy = false;
      throw error;
    }
    const started = Date.now();
    const job: Job = {
      view: {
        job_id: id,
        kind,
        state: "running",
        ready_to_edit: false,
        verified: false,
        created_at: new Date(started).toISOString(),
        duration_ms: 0,
        progress: [],
      },
      controller: new AbortController(),
      task: Promise.resolve(),
    };
    for (const [key, value] of this.jobs) {
      if (this.jobs.size < 100) break;
      if (!["running", "cancelling"].includes(value.view.state)) this.jobs.delete(key);
    }
    this.jobs.set(id, job);
    const report = (message: string) => {
      job.view.progress.push(message.slice(-2000));
      job.view.progress = job.view.progress.slice(-20);
      console.error(message);
    };
    job.task = (async () => {
      try {
        const outcome = await action(job.controller.signal, report);
        job.controller.signal.throwIfAborted();
        job.view.state = outcome.state;
        job.view.result = outcome.result;
        job.view.ready_to_edit = kind === "lock_behavior" && outcome.state === "completed";
        job.view.verified = kind === "verify_behavior" && outcome.state === "completed";
      } catch (error) {
        job.view.state = job.controller.signal.aborted ? "cancelled" : "failed";
        job.view.error = error instanceof Error ? error.message : String(error);
      } finally {
        job.view.duration_ms = Date.now() - started;
        try {
          await release();
        } catch (error) {
          job.view.state = "failed";
          job.view.ready_to_edit = job.view.verified = false;
          job.view.error = `Could not release repository lock: ${error}`;
        }
        this.busy = false;
      }
    })();
    return this.view(job);
  }

  private view(job: Job): JobView {
    const view = structuredClone(job.view);
    if (["running", "cancelling"].includes(view.state)) {
      view.duration_ms = Date.now() - Date.parse(view.created_at);
    }
    return view;
  }

  async lock(input: LockInput): Promise<JobView> {
    const scope = await resolveScope(this.root, {
      all: input.scope.all ?? false,
      paths: input.scope.paths ?? [],
      classes: input.scope.classes ?? [],
      selections: input.scope.selections ?? [],
    });
    const paths = await testPaths(this.root, input.test_paths);
    return await this.start("lock_behavior", async (signal, report) => {
      await (this.dependencies.token ?? bearerToken)();
      signal.throwIfAborted();
      const before = await snapshot(this.root);
      const selected = Object.keys(before).filter((path) =>
        path.endsWith(".java") && (scope.projectWide || under(path, scope.selectedPaths))
      );
      if (!scope.projectWide && selected.some((path) => under(path, paths))) {
        throw new Error("test_paths must not include the selected production code");
      }
      const protectedFiles = select(before, paths, false);
      const workflow = await (this.dependencies.run ?? runWorkflow)(
        this.root,
        "lock_behavior",
        scope,
        signal,
        {
          userRequest: [
            "Prepare a behavior baseline before the calling coding agent edits production code.",
            `Planned change: ${input.intent ?? "Preserve existing behavior during a refactor."}`,
            `Only create or edit tests within these repository paths: ${JSON.stringify(paths)}.`,
            "Preserve all other files, including existing production code and build configuration.",
            `Validate with this command: ${input.test_command}`,
            "Do not change existing behavior to match the planned change; characterize it as it is.",
          ].join("\n"),
          report,
          execute: (root, command, commandSignal) => {
            const requested = command.timeoutSeconds;
            // Apply the caller's time budget consistently, including model-requested commands.
            // Keep invalid requests invalid so the shared runner still validates their shape.
            const timeoutSeconds = typeof requested === "number" && Number.isInteger(requested) &&
                requested >= 1 && requested <= 7200
              ? Math.max(requested, input.timeout_seconds)
              : requested;
            return (this.dependencies.execute ?? runCommand)(
              root,
              { ...command, timeoutSeconds },
              commandSignal,
            );
          },
        },
      );
      signal.throwIfAborted();
      const after = await snapshot(this.root);
      const modified = changed(protectedFiles, select(after, paths, false));
      if (modified.length || workflow.status !== "complete") {
        return {
          state: "blocked",
          result: { workflow, changed_protected_paths: modified, baseline_id: null },
        };
      }
      const tests = select(after, paths, true);
      if (!Object.keys(tests).length) {
        return { state: "blocked", result: { reason: "No baseline test files found", workflow } };
      }
      await testPaths(this.root, Object.keys(tests));
      report("JAIPilot MCP: independently verifying the baseline…");
      const verification = await (this.dependencies.execute ?? runCommand)(this.root, {
        command: input.test_command,
        purpose: "baseline tests",
        timeoutSeconds: input.timeout_seconds,
      }, signal);
      const final = await snapshot(this.root);
      const modifiedDuringVerification = changed(after, final);
      if (verification.exitCode !== 0 || modifiedDuringVerification.length) {
        return {
          state: "blocked",
          result: {
            workflow,
            verification,
            changed_during_verification: modifiedDuringVerification,
            baseline_id: null,
          },
        };
      }
      signal.throwIfAborted();
      const baseline: Baseline = {
        formatVersion: 1,
        id: crypto.randomUUID(),
        repository: this.root,
        createdAt: new Date().toISOString(),
        scope,
        intent: input.intent ?? "Preserve existing behavior during a refactor.",
        testPaths: paths,
        testCommand: input.test_command,
        timeoutSeconds: input.timeout_seconds,
        protectedFiles,
        testFiles: tests,
        verification,
      };
      await saveBaseline(this.root, baseline);
      return {
        state: "completed",
        result: {
          baseline_id: baseline.id,
          scope,
          test_paths: Object.keys(tests),
          verification,
          workflow,
        },
      };
    });
  }

  async verify(id: string): Promise<JobView> {
    const baseline = await loadBaseline(this.root, id);
    return await this.start("verify_behavior", async (signal, report) => {
      const before = await snapshot(this.root);
      const modifiedTests = changed(baseline.testFiles, select(before, baseline.testPaths, true));
      if (modifiedTests.length) {
        return {
          state: "blocked",
          result: {
            baseline_id: id,
            reason: "Baseline tests changed; restore them before verification",
            changed_test_paths: modifiedTests,
          },
        };
      }
      report("JAIPilot MCP: verifying preserved behavior after edits…");
      const verification = await (this.dependencies.execute ?? runCommand)(this.root, {
        command: baseline.testCommand,
        purpose: "preserved baseline tests",
        timeoutSeconds: baseline.timeoutSeconds,
      }, signal);
      const after = await snapshot(this.root);
      const modifiedDuringVerification = changed(before, after);
      return {
        state: verification.exitCode === 0 && !modifiedDuringVerification.length
          ? "completed"
          : "blocked",
        result: {
          baseline_id: id,
          verification,
          changed_production_paths: changed(
            baseline.protectedFiles,
            select(before, baseline.testPaths, false),
          ),
          changed_during_verification: modifiedDuringVerification,
        },
      };
    });
  }

  async status(id: string, waitSeconds = 0): Promise<JobView> {
    const job = this.jobs.get(id);
    if (!job) throw new Error(`Job not found: ${id}. Jobs belong to this MCP server instance.`);
    if (waitSeconds && ["running", "cancelling"].includes(job.view.state)) {
      let timer: ReturnType<typeof setTimeout> | undefined;
      try {
        await Promise.race([
          job.task,
          new Promise<void>((resolve) => {
            timer = setTimeout(resolve, waitSeconds * 1000);
          }),
        ]);
      } finally {
        clearTimeout(timer);
      }
    }
    return this.view(job);
  }

  cancel(id: string): JobView {
    const job = this.jobs.get(id);
    if (!job) throw new Error(`Job not found: ${id}`);
    if (job.view.state === "running") {
      job.view.state = "cancelling";
      job.controller.abort();
    }
    return this.view(job);
  }

  async stop(): Promise<void> {
    this.stopped = true;
    for (const job of this.jobs.values()) this.cancel(job.view.job_id);
    await Promise.allSettled([...this.jobs.values()].map((job) => job.task));
  }
}
