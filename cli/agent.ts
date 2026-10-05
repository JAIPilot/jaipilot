import { bearerToken } from "./auth.ts";
import { spawn } from "node:child_process";
import process from "node:process";
import { git, projectContext, type Scope } from "./project.ts";

const ENDPOINT = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/invoke-testing-agent";
const PROTOCOL_VERSION = 6;
const MAX_OUTPUT = 80_000;

type Block = {
  type: string;
  id?: string;
  name?: string;
  input?: Record<string, unknown>;
  text?: string;
};
type Reply = {
  content?: Block[];
  continue?: boolean;
  parallel?: { completedClasses: number; totalClasses: number };
  error?: string;
  pending?: boolean;
};
export type WorkflowResult = {
  workflow: string;
  scope: Scope;
  status: "complete" | "blocked";
  summary: string;
  verification: Record<string, unknown>;
  testFailures: unknown[];
  nextActions: unknown[];
  gitStatus: string;
  durationMs: number;
};

async function request(
  method: "GET" | "POST",
  body?: unknown,
  signal?: AbortSignal,
): Promise<Record<string, unknown>> {
  const token = await bearerToken();
  const id = body && typeof body === "object" && "requestId" in body ? body.requestId : undefined;
  const started = Date.now();
  let transientFailures = 0;
  let waiting = false;
  while (true) {
    if (signal?.aborted) throw new Error("Cancelled");
    if (Date.now() - started > 15 * 60_000) {
      throw new Error(`JAIPilot request timed out${id ? ` (${id})` : ""}`);
    }
    let response: Response;
    try {
      response = await fetch(ENDPOINT, {
        method,
        headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.any([
          signal ?? new AbortController().signal,
          AbortSignal.timeout(145_000),
        ]),
      });
    } catch {
      if (signal?.aborted) throw new Error("Cancelled");
      transientFailures++;
      await pause(Math.min(3000 * 2 ** Math.min(transientFailures - 1, 4), 30_000), signal);
      continue;
    }
    let value: Record<string, unknown>;
    try {
      value = await response.json();
    } catch (error) {
      if (error instanceof TypeError) {
        if (signal?.aborted) throw new Error("Cancelled");
        transientFailures++;
        await pause(Math.min(3000 * 2 ** Math.min(transientFailures - 1, 4), 30_000), signal);
        continue;
      }
      value = { error: `JAIPilot returned HTTP ${response.status}` };
    }
    const pending = response.status === 202;
    const transient = value.retryable !== false &&
      ([408, 425, 429].includes(response.status) || response.status >= 500);
    if (pending || transient) {
      if (!waiting) {
        console.error(`JAIPilot: waiting for the service (HTTP ${response.status})…`);
        waiting = true;
      }
      const retry = Number(response.headers.get("retry-after") ?? NaN);
      if (transient) transientFailures++;
      await pause(
        Number.isFinite(retry) && retry >= 0
          ? Math.min(retry * 1000, 30_000)
          : pending
          ? 3000
          : Math.min(3000 * 2 ** Math.min(transientFailures - 1, 4), 30_000),
        signal,
      );
      continue;
    }
    if (!response.ok) {
      const reference = id ? ` (request ${id})` : "";
      throw new Error(`${String(value.error ?? `HTTP ${response.status}`)}${reference}`);
    }
    return value;
  }
}

function pause(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      signal?.removeEventListener("abort", cancel);
      resolve();
    }, ms);
    const cancel = () => {
      clearTimeout(timer);
      reject(new Error("Cancelled"));
    };
    signal?.addEventListener("abort", cancel, { once: true });
    if (signal?.aborted) cancel();
  });
}

export async function workflows(
  signal?: AbortSignal,
): Promise<{ id: string; title: string; description: string }[]> {
  const response = await request("GET", undefined, signal);
  if (response.protocolVersion !== PROTOCOL_VERSION || !Array.isArray(response.workflows)) {
    throw new Error("JAIPilot returned an incompatible workflow catalog");
  }
  return response.workflows as { id: string; title: string; description: string }[];
}

export async function runCommand(
  root: string,
  input: Record<string, unknown>,
  signal?: AbortSignal,
) {
  const command = input.command;
  const timeoutSeconds = input.timeoutSeconds;
  if (
    typeof command !== "string" || !command.trim() || command.includes("\0") ||
    !Number.isInteger(timeoutSeconds) || Number(timeoutSeconds) < 1 || Number(timeoutSeconds) > 7200
  ) {
    throw new Error("Invalid local command request");
  }
  if (signal?.aborted) throw new Error("Cancelled");
  const purpose = typeof input.purpose === "string" ? input.purpose : "task";
  console.error(`JAIPilot: ${purpose}…`);
  const start = performance.now();
  const windows = Deno.build.os === "windows";
  // A process group lets cancellation stop Maven/Java grandchildren as well as the shell.
  const child = spawn(
    windows ? "cmd.exe" : "/bin/sh",
    windows ? ["/d", "/s", "/c", command] : ["-lc", command],
    { cwd: root, detached: !windows, stdio: ["ignore", "pipe", "pipe"] },
  );
  const out = { text: "", truncated: false }, err = { text: "", truncated: false };
  for (const [stream, target] of [[child.stdout!, out], [child.stderr!, err]] as const) {
    stream.setEncoding("utf8").on("data", (chunk: string) => {
      target.text += chunk;
      if (target.text.length > MAX_OUTPUT) {
        target.text = target.text.slice(-MAX_OUTPUT);
        target.truncated = true;
      }
    });
  }
  const completed = new Promise<number>((resolve, reject) => {
    child.once("error", reject);
    child.once("close", (code) => resolve(code ?? 1));
  });
  let timedOut = false;
  let terminating = false;
  let escalation: ReturnType<typeof setTimeout> | undefined;
  let termination: Promise<void> | undefined;
  const killGroup = (signal: NodeJS.Signals) => {
    try {
      if (child.pid) process.kill(-child.pid, signal);
    } catch { /* Already exited. */ }
  };
  const cancel = () => {
    if (terminating || !child.pid) return;
    terminating = true;
    if (windows) {
      termination = new Deno.Command("taskkill", {
        args: ["/PID", String(child.pid), "/T", "/F"],
        stdout: "null",
        stderr: "null",
      }).output().then(() => {
        child.kill("SIGKILL");
      }).catch(() => {
        child.kill("SIGKILL");
      });
    } else {
      killGroup("SIGTERM");
      escalation = setTimeout(() => killGroup("SIGKILL"), 2000);
    }
  };
  const timer = setTimeout(() => {
    timedOut = true;
    cancel();
  }, Number(timeoutSeconds) * 1000);
  signal?.addEventListener("abort", cancel, { once: true });
  if (signal?.aborted) cancel();
  try {
    const code = await completed;
    await termination;
    if (signal?.aborted) throw new Error("Cancelled");
    const merged = [out.text, err.text].filter(Boolean).join("\n");
    return {
      command,
      exitCode: timedOut ? 124 : code,
      output: merged.length > MAX_OUTPUT
        ? `\n… earlier command output omitted …\n${merged.slice(-MAX_OUTPUT)}`
        : merged,
      truncated: out.truncated || err.truncated || merged.length > MAX_OUTPUT,
      timedOut,
      durationMs: Math.round(performance.now() - start),
    };
  } finally {
    clearTimeout(timer);
    clearTimeout(escalation);
    if (terminating && !windows) killGroup("SIGKILL");
    signal?.removeEventListener("abort", cancel);
  }
}

export async function runWorkflow(
  root: string,
  workflow: string,
  scope: Scope,
  signal?: AbortSignal,
  options: {
    protocolVersion?: 4 | 6;
    userRequest?: string;
    report?: (message: string) => void | Promise<void>;
    execute?: typeof runCommand;
  } = {},
): Promise<WorkflowResult> {
  const started = performance.now();
  const catalog = await workflows(signal);
  const report = options.report ?? console.error;
  if (!catalog.some((item) => item.id === workflow)) {
    throw new Error(`Unknown workflow: ${workflow}. Run \`jaipilot workflows\` to list them.`);
  }
  const project = await projectContext(root, scope);
  const context = {
    workflow,
    revision: 0,
    project,
    selections: scope.selections,
    trigger: { projectWide: scope.projectWide },
    jobId: crypto.randomUUID(),
    ...(options.userRequest ? { userRequest: options.userRequest } : {}),
  };
  const history: Record<string, unknown>[] = [];
  for (let turn = 0; turn < 300; turn++) {
    const body = {
      protocolVersion: options.protocolVersion ?? PROTOCOL_VERSION,
      requestId: crypto.randomUUID(),
      workflow,
      context,
      history,
    };
    const reply = await request("POST", body, signal) as Reply;
    if (reply.continue) {
      const progress = reply.parallel;
      if (progress?.totalClasses) {
        await report(
          `JAIPilot: ${progress.completedClasses}/${progress.totalClasses} classes complete`,
        );
      }
      continue;
    }
    if (!Array.isArray(reply.content)) throw new Error("JAIPilot returned an invalid agent turn");
    for (const block of reply.content) {
      if (block.type === "text" && block.text?.trim()) await report(block.text.trim());
    }
    const results: Record<string, unknown>[] = [];
    for (const block of reply.content) {
      if (block.type !== "tool_use") continue;
      if (block.name === "finish") {
        if (!block.input || !["complete", "blocked"].includes(String(block.input.status))) {
          throw new Error("JAIPilot returned an invalid result");
        }
        const gitStatus = await git(root, "status", "--short");
        return {
          workflow,
          scope,
          ...block.input,
          gitStatus,
          durationMs: Math.round(performance.now() - started),
        } as WorkflowResult;
      }
      if (!block.id || !block.name || !block.input) {
        throw new Error("JAIPilot returned an invalid tool request");
      }
      let value: unknown, error = false;
      try {
        if (block.name !== "run_command") throw new Error(`Unsupported local tool: ${block.name}`);
        value = await (options.execute ?? runCommand)(root, block.input, signal);
      } catch (failure) {
        if (signal?.aborted) throw failure;
        value = { error: failure instanceof Error ? failure.message : "Local tool failed" };
        error = true;
      }
      results.push({
        type: "tool_result",
        tool_use_id: block.id,
        content: JSON.stringify(value),
        ...(error ? { is_error: true } : {}),
      });
    }
    if (!results.length) throw new Error("JAIPilot did not request a local tool");
    history.push({ role: "assistant", content: reply.content }, { role: "user", content: results });
  }
  throw new Error("JAIPilot stopped after 300 agent turns");
}
