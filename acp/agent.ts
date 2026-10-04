import * as acp from "npm:@agentclientprotocol/sdk@1.7.0";
import { isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runWorkflow, type WorkflowResult } from "../cli/agent.ts";
import { bearerToken, login, logout } from "../cli/auth.ts";
import { repositoryRoot, resolveScope } from "../cli/project.ts";
import { VERSION } from "../cli/version.ts";

export const OUTCOMES = [
  {
    id: "generate_tests",
    name: "Generate tests",
    description: "Create focused Java tests and verify them.",
  },
  {
    id: "improve_coverage",
    name: "Improve coverage",
    description: "Measure coverage and close meaningful gaps.",
  },
  {
    id: "fix_tests",
    name: "Fix failing tests",
    description: "Reproduce failures, repair tests, and rerun the suite.",
  },
  {
    id: "stabilize_flaky_tests",
    name: "Stabilize flaky tests",
    description: "Reproduce nondeterminism and verify the fix repeatedly.",
  },
  {
    id: "test_current_changes",
    name: "Test current changes",
    description: "Test behavior affected by the current Git diff.",
  },
  {
    id: "lock_behavior",
    name: "Lock existing behavior",
    description: "Capture observable behavior before refactoring.",
  },
];

const AUTH = {
  id: "jaipilot-login",
  name: "Sign in to JAIPilot",
  description:
    "Open your browser and use your existing JAIPilot account, subscription, and credits.",
  type: "agent" as const,
};
const SCOPE_HINT =
  "--class com.acme.OrderService | --path src/main/java | --selection File.java:42-88 | --all";
const HELP = `Choose a Java testing outcome, then supply exactly one scope.\n\n${
  OUTCOMES.map((o) => `/${o.id} — ${o.description}`).join("\n")
}\n\nExample: /improve_coverage --class com.acme.OrderService\n\nScope: ${SCOPE_HINT}\nYou can repeat --class, --path, or --selection. An attached file link can supply the scope. Save editor changes before running. Commands and their output appear in the IDE; review the complete diff afterward.\n\nJAIPilot uses its managed model service and your account credits. Approved command output and project metadata are sent to that service. No remote workspace or bulk source upload is used by this adapter. Custom MCP servers, images, audio, and persistent sessions are not supported.`;

/** Tokenize scope arguments without ever interpreting them as shell code. */
export function scopeArguments(text: string): string[] {
  const values: string[] = [];
  let current = "", quote = "", started = false;
  for (const char of text.trim()) {
    if (quote) {
      if (char === quote) quote = "";
      else current += char;
    } else if ((char === '"' || char === "'") && !started) {
      quote = char;
      started = true;
    } else if (/\s/.test(char)) {
      if (started) values.push(current);
      current = "";
      started = false;
    } else {
      current += char;
      started = true;
    }
  }
  if (quote) throw new Error("Close the quoted scope argument.");
  if (started) values.push(current);
  return values;
}

export async function promptScope(
  root: string,
  text: string,
  prompt: acp.ContentBlock[],
  cwd = root,
) {
  const args = scopeArguments(text);
  const options = {
    all: false,
    paths: [] as string[],
    classes: [] as string[],
    selections: [] as string[],
  };
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--all") options.all = true;
    else if (["--class", "--path", "--selection"].includes(arg)) {
      const value = args[++i];
      if (!value || value.startsWith("--")) throw new Error(`Missing value for ${arg}`);
      if (arg === "--class") options.classes.push(value);
      if (arg === "--path") options.paths.push(resolve(cwd, value));
      if (arg === "--selection") {
        const match = value.match(/^(.+):(\d+(?:-\d+)?)$/);
        if (!match) throw new Error("Use --selection path:start-end");
        options.selections.push(`${resolve(cwd, match[1])}:${match[2]}`);
      }
    } else if (arg.startsWith("--")) throw new Error(`Unknown scope option: ${arg}`);
  }
  if (
    !options.all && !options.paths.length && !options.classes.length && !options.selections.length
  ) {
    for (const block of prompt) {
      const uri = block.type === "resource_link"
        ? block.uri
        : block.type === "resource"
        ? block.resource.uri
        : null;
      if (uri?.startsWith("file:")) options.paths.push(fileURLToPath(uri));
    }
  }
  return await resolveScope(root, options);
}

function configOptions(mode: string): acp.SessionConfigOption[] {
  return [{
    id: "outcome",
    name: "Java testing outcome",
    category: "mode",
    type: "select",
    currentValue: mode,
    options: OUTCOMES.map(({ id, ...rest }) => ({ value: id, ...rest })),
  }];
}

function outcome(id: string) {
  const value = OUTCOMES.find((item) => item.id === id);
  if (!value) throw acp.RequestError.invalidParams(undefined, `Unknown testing outcome: ${id}`);
  return value;
}

type Session = { root: string; cwd: string; mode: string; active?: AbortController };
type Dependencies = { token: typeof bearerToken; login: typeof login; run: typeof runWorkflow };

function interrupted<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const abort = () => reject(signal.reason);
    signal.addEventListener("abort", abort, { once: true });
    promise.then(resolve, reject).finally(() => signal.removeEventListener("abort", abort));
  });
}

export class TestingAgent {
  private sessions = new Map<string, Session>();
  private initialized = false;
  private terminal = false;
  // Serialize repository mutations even across separate IDE chat sessions.
  private running = new Set<string>();
  private finishing = new Set<Promise<void>>();
  constructor(private deps: Dependencies = { token: bearerToken, login, run: runWorkflow }) {}

  initialize(params: acp.InitializeRequest): acp.InitializeResponse {
    this.initialized = true;
    this.terminal = params.clientCapabilities?.terminal === true;
    return {
      protocolVersion: acp.PROTOCOL_VERSION,
      agentInfo: { name: "jaipilot", title: "JAIPilot", version: VERSION },
      agentCapabilities: {
        loadSession: false,
        promptCapabilities: { embeddedContext: true },
        auth: { logout: {} },
      },
      authMethods: [AUTH],
    };
  }

  async authenticate(
    params: acp.AuthenticateRequest,
    signal: AbortSignal,
  ): Promise<acp.AuthenticateResponse> {
    if (!this.initialized || params.methodId !== AUTH.id) throw acp.RequestError.invalidParams();
    await this.deps.login({ signal });
    return {};
  }

  async requireAuth() {
    try {
      await this.deps.token();
    } catch (error) {
      throw acp.RequestError.authRequired(
        { authMethods: [AUTH] },
        error instanceof Error ? error.message : "Sign in to JAIPilot.",
      );
    }
  }

  async newSession(
    params: acp.NewSessionRequest,
    client: acp.AgentContext,
  ): Promise<acp.NewSessionResponse> {
    if (!this.initialized) throw acp.RequestError.invalidParams(undefined, "Initialize first.");
    if (!isAbsolute(params.cwd)) {
      throw acp.RequestError.invalidParams(undefined, "cwd must be absolute.");
    }
    if (params.mcpServers.length) {
      throw acp.RequestError.invalidParams(
        undefined,
        "JAIPilot's bounded testing adapter does not use custom MCP servers. Disable Pass custom MCP servers and Pass IntelliJ MCP server for this agent.",
      );
    }
    if (!this.terminal) {
      throw acp.RequestError.invalidParams(
        undefined,
        "This agent requires ACP client terminal support for approved local commands.",
      );
    }
    await this.requireAuth();
    const root = await repositoryRoot(params.cwd);
    const sessionId = crypto.randomUUID();
    const mode = "generate_tests";
    this.sessions.set(sessionId, { root, cwd: await Deno.realPath(params.cwd), mode });
    await client.notify("session/update", {
      sessionId,
      update: {
        sessionUpdate: "available_commands_update",
        availableCommands: [
          ...OUTCOMES.map((item) => ({
            name: item.id,
            description: item.description,
            input: { hint: SCOPE_HINT },
          })),
          {
            name: "help",
            description: "Show testing outcomes, scope syntax, and privacy details.",
          },
        ],
      },
    });
    return {
      sessionId,
      modes: { currentModeId: mode, availableModes: OUTCOMES },
      configOptions: configOptions(mode),
    };
  }

  private session(id: string): Session {
    const session = this.sessions.get(id);
    if (!session) throw acp.RequestError.invalidParams(undefined, "Unknown session.");
    return session;
  }

  async setMode(sessionId: string, modeId: string, client: acp.AgentContext) {
    const session = this.session(sessionId);
    outcome(modeId);
    session.mode = modeId;
    await client.notify("session/update", {
      sessionId,
      update: { sessionUpdate: "current_mode_update", currentModeId: modeId },
    });
    await client.notify("session/update", {
      sessionId,
      update: { sessionUpdate: "config_option_update", configOptions: configOptions(modeId) },
    });
    return {};
  }

  async setConfig(
    params: acp.SetSessionConfigOptionRequest,
    client: acp.AgentContext,
  ): Promise<acp.SetSessionConfigOptionResponse> {
    if (params.configId !== "outcome" || typeof params.value !== "string") {
      throw acp.RequestError.invalidParams();
    }
    await this.setMode(params.sessionId, params.value, client);
    return { configOptions: configOptions(params.value) };
  }

  cancel(sessionId: string) {
    this.sessions.get(sessionId)?.active?.abort();
  }
  async stop() {
    for (const session of this.sessions.values()) session.active?.abort();
    await Promise.all(this.finishing);
  }

  async prompt(
    params: acp.PromptRequest,
    client: acp.AgentContext,
    requestSignal: AbortSignal,
  ): Promise<acp.PromptResponse> {
    const session = this.session(params.sessionId);
    if (session.active || this.running.has(session.root)) {
      throw acp.RequestError.invalidParams(
        undefined,
        "A testing workflow is already running in this repository. Cancel it or wait for completion.",
      );
    }
    const controller = new AbortController();
    let finish!: () => void;
    const finished = new Promise<void>((resolve) => finish = resolve);
    this.finishing.add(finished);
    session.active = controller;
    this.running.add(session.root);
    const signal = AbortSignal.any([controller.signal, requestSignal]);
    const message = (text: string) =>
      client.notify("session/update", {
        sessionId: params.sessionId,
        update: {
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text: text + "\n\n" },
        },
      });
    try {
      signal.throwIfAborted();
      if (params.prompt.some((p) => !["text", "resource", "resource_link"].includes(p.type))) {
        throw new Error(
          "Use a text prompt and Java file links; images and audio are not supported.",
        );
      }
      const text = params.prompt.flatMap((p) => p.type === "text" ? [p.text] : []).join("\n")
        .trim();
      const slash = text.match(/^\/([a-z_]+)(?:\s+([\s\S]*))?$/);
      if (
        slash?.[1] === "help" ||
        !text && !params.prompt.some((p) => p.type === "resource_link" || p.type === "resource")
      ) {
        await message(HELP);
        return { stopReason: "end_turn" };
      }
      const selected = outcome(slash?.[1] ?? session.mode);
      const input = slash ? slash[2] ?? "" : text;
      const scope = await promptScope(session.root, input, params.prompt, session.cwd);
      // Absolute scope paths remain rooted in the Git repository even when the IDE cwd is a module.
      await this.requireAuth();
      await this.setMode(params.sessionId, selected.id, client);
      await message(
        `${selected.name}: ${scope.selectedPaths.join(", ")}${
          scope.selections.length ? " (selected lines)" : ""
        }. Using your existing build and test tools.`,
      );
      const serviceConsentId = crypto.randomUUID();
      await this.permission(params.sessionId, client, {
        toolCallId: serviceConsentId,
        title: "Authorize the JAIPilot managed testing service",
        kind: "fetch",
        status: "pending",
        content: [{
          type: "content",
          content: {
            type: "text",
            text:
              "This run uses your JAIPilot account credits and sends selected paths, project metadata, and approved local command output to JAIPilot and its model provider. Commands can read source into their output. No remote workspace or bulk source archive is used. Review each command before approving it.",
          },
        }],
      }, signal);
      await client.notify("session/update", {
        sessionId: params.sessionId,
        update: {
          sessionUpdate: "tool_call_update",
          toolCallId: serviceConsentId,
          status: "completed",
        },
      });
      const result = await this.deps.run(session.root, selected.id, scope, signal, {
        // v4 exposes only local tools: never start the v5/v6 parallel source-upload workers.
        protocolVersion: 4,
        userRequest: text,
        report: message,
        execute: (root, input, commandSignal) =>
          this.command(params.sessionId, root, input, client, commandSignal ?? signal),
      });
      signal.throwIfAborted();
      await message(resultText(result));
      return { stopReason: "end_turn" };
    } catch (error) {
      if (signal.aborted) return { stopReason: "cancelled" };
      if (error instanceof acp.RequestError && error.code === -32000) throw error;
      await message(
        `Blocked: ${error instanceof Error ? error.message : String(error)}\n\n${SCOPE_HINT}`,
      );
      return { stopReason: "end_turn" };
    } finally {
      session.active = undefined;
      this.running.delete(session.root);
      this.finishing.delete(finished);
      finish();
    }
  }

  private async permission(
    sessionId: string,
    client: acp.AgentContext,
    toolCall: acp.ToolCall,
    signal: AbortSignal,
  ) {
    await client.notify("session/update", {
      sessionId,
      update: { sessionUpdate: "tool_call", ...toolCall },
    });
    const response = await interrupted(
      client.request("session/request_permission", {
        sessionId,
        toolCall,
        options: [
          { optionId: "allow", name: "Allow once", kind: "allow_once" },
          { optionId: "reject", name: "Reject", kind: "reject_once" },
        ],
      }),
      signal,
    );
    signal.throwIfAborted();
    if (response.outcome.outcome !== "selected" || response.outcome.optionId !== "allow") {
      await client.notify("session/update", {
        sessionId,
        update: {
          sessionUpdate: "tool_call_update",
          toolCallId: toolCall.toolCallId,
          status: "failed",
        },
      });
      this.cancel(sessionId);
      throw new Error("Permission rejected; workflow stopped.");
    }
  }

  private async command(
    sessionId: string,
    root: string,
    input: Record<string, unknown>,
    client: acp.AgentContext,
    signal: AbortSignal,
  ) {
    const { command, timeoutSeconds } = input;
    if (
      typeof command !== "string" || !command.trim() || command.includes("\0") ||
      !Number.isInteger(timeoutSeconds) || Number(timeoutSeconds) < 1 ||
      Number(timeoutSeconds) > 7200
    ) throw new Error("Invalid local command request");
    const toolCallId = crypto.randomUUID();
    const toolCall: acp.ToolCall = {
      toolCallId,
      title: String(input.purpose ?? "Run command"),
      kind: "execute",
      status: "pending",
      rawInput: { command, cwd: root, timeoutSeconds },
      content: [{ type: "content", content: { type: "text", text: command } }],
    };
    await this.permission(sessionId, client, toolCall, signal);
    signal.throwIfAborted();
    const started = performance.now();
    const terminal = await client.request("terminal/create", {
      sessionId,
      command: Deno.build.os === "windows" ? "cmd.exe" : "/bin/sh",
      args: Deno.build.os === "windows" ? ["/d", "/s", "/c", command] : ["-lc", command],
      cwd: root,
      outputByteLimit: 80_000,
    });
    const params = { sessionId, terminalId: terminal.terminalId };
    let timedOut = false;
    try {
      signal.throwIfAborted();
      await client.notify("session/update", {
        sessionId,
        update: {
          sessionUpdate: "tool_call_update",
          toolCallId,
          status: "in_progress",
          content: [{ type: "terminal", terminalId: terminal.terminalId }],
        },
      });
      const deadline = AbortSignal.timeout(Number(timeoutSeconds) * 1000);
      let exit: acp.WaitForTerminalExitResponse;
      try {
        exit = await interrupted(
          client.request("terminal/wait_for_exit", params),
          AbortSignal.any([signal, deadline]),
        );
      } catch (error) {
        await client.request("terminal/kill", params);
        if (signal.aborted || !deadline.aborted) throw error;
        timedOut = true;
        exit = { exitCode: 124 };
      }
      const output = await client.request<acp.TerminalOutputResponse>("terminal/output", params);
      const value = {
        command,
        exitCode: timedOut ? 124 : exit.exitCode ?? 1,
        output: output.output,
        truncated: output.truncated,
        timedOut,
        durationMs: Math.round(performance.now() - started),
      };
      await client.notify("session/update", {
        sessionId,
        update: {
          sessionUpdate: "tool_call_update",
          toolCallId,
          status: value.exitCode === 0 ? "completed" : "failed",
          rawOutput: value,
        },
      });
      return value;
    } finally {
      // Release also kills a running command; it is required after errors and cancellation.
      await client.request("terminal/release", params);
    }
  }
}

function resultText(result: WorkflowResult) {
  const evidence: Record<string, unknown> = {
    ...result.verification,
    ...(result.testFailures?.length ? { testFailures: result.testFailures } : {}),
    ...(result.nextActions?.length ? { nextActions: result.nextActions } : {}),
  };
  const verification = Object.entries(evidence).filter(([, value]) => value != null && value !== "")
    .map(([key, value]) => `- ${key}: ${typeof value === "string" ? value : JSON.stringify(value)}`)
    .join("\n");
  return `${
    result.status === "complete" ? "Completed" : "Blocked"
  }: ${result.summary}\n\n${verification}\n\nTime taken: ${
    (result.durationMs / 1000).toFixed(1)
  }s.\n\nGit status:\n\`\`\`text\n${
    result.gitStatus || "No changes"
  }\n\`\`\`\nReview the complete diff in your IDE before accepting changes.`;
}

export function testingApp(agent = new TestingAgent()) {
  return acp.agent({ name: "jaipilot" })
    .onRequest("initialize", (ctx) => agent.initialize(ctx.params))
    .onRequest("authenticate", (ctx) => agent.authenticate(ctx.params, ctx.signal))
    .onRequest("logout", async () => {
      await agent.stop();
      await logout();
      return {};
    })
    .onRequest("session/new", (ctx) => agent.newSession(ctx.params, ctx.client))
    .onRequest(
      "session/set_mode",
      (ctx) => agent.setMode(ctx.params.sessionId, ctx.params.modeId, ctx.client),
    )
    .onRequest("session/set_config_option", (ctx) => agent.setConfig(ctx.params, ctx.client))
    .onRequest("session/prompt", (ctx) => agent.prompt(ctx.params, ctx.client, ctx.signal))
    .onNotification("session/cancel", (ctx) => agent.cancel(ctx.params.sessionId));
}
