import { bearerToken } from "./auth.ts";
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
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => {
      clearTimeout(timer);
      reject(new Error("Cancelled"));
    }, { once: true });
  });
}

export async function workflows(): Promise<{ id: string; title: string; description: string }[]> {
  const response = await request("GET");
  if (response.protocolVersion !== PROTOCOL_VERSION || !Array.isArray(response.workflows)) {
    throw new Error("JAIPilot returned an incompatible workflow catalog");
  }
  return response.workflows as { id: string; title: string; description: string }[];
}

async function tail(
  stream: ReadableStream<Uint8Array>,
): Promise<{ text: string; truncated: boolean }> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let text = "", truncated = false;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    text += decoder.decode(value, { stream: true });
    if (text.length > MAX_OUTPUT) {
      text = text.slice(-MAX_OUTPUT);
      truncated = true;
    }
  }
  text += decoder.decode();
  return { text, truncated };
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
  const purpose = typeof input.purpose === "string" ? input.purpose : "task";
  console.error(`JAIPilot: ${purpose}…`);
  const start = performance.now();
  const child = new Deno.Command(Deno.build.os === "windows" ? "cmd.exe" : "/bin/sh", {
    args: Deno.build.os === "windows" ? ["/d", "/s", "/c", command] : ["-lc", command],
    cwd: root,
    stdin: "null",
    stdout: "piped",
    stderr: "piped",
  }).spawn();
  const stdout = tail(child.stdout);
  const stderr = tail(child.stderr);
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    try {
      child.kill();
    } catch { /* exited */ }
  }, Number(timeoutSeconds) * 1000);
  const cancel = () => {
    try {
      child.kill();
    } catch { /* exited */ }
  };
  signal?.addEventListener("abort", cancel, { once: true });
  try {
    const [status, out, err] = await Promise.all([child.status, stdout, stderr]);
    if (signal?.aborted) throw new Error("Cancelled");
    const merged = [out.text, err.text].filter(Boolean).join("\n");
    return {
      command,
      exitCode: timedOut ? 124 : status.code,
      output: merged.length > MAX_OUTPUT
        ? `\n… earlier command output omitted …\n${merged.slice(-MAX_OUTPUT)}`
        : merged,
      truncated: out.truncated || err.truncated || merged.length > MAX_OUTPUT,
      timedOut,
      durationMs: Math.round(performance.now() - start),
    };
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", cancel);
  }
}

export async function runWorkflow(
  root: string,
  workflow: string,
  scope: Scope,
  signal?: AbortSignal,
): Promise<WorkflowResult> {
  const started = performance.now();
  const catalog = await workflows();
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
  };
  const history: Record<string, unknown>[] = [];
  for (let turn = 0; turn < 300; turn++) {
    const body = {
      protocolVersion: PROTOCOL_VERSION,
      requestId: crypto.randomUUID(),
      workflow,
      context,
      history,
    };
    const reply = await request("POST", body, signal) as Reply;
    if (reply.continue) {
      const progress = reply.parallel;
      if (progress?.totalClasses) {
        console.error(
          `JAIPilot: ${progress.completedClasses}/${progress.totalClasses} classes complete`,
        );
      }
      continue;
    }
    if (!Array.isArray(reply.content)) throw new Error("JAIPilot returned an invalid agent turn");
    for (const block of reply.content) {
      if (block.type === "text" && block.text?.trim()) console.error(block.text.trim());
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
        value = await runCommand(root, block.input, signal);
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
