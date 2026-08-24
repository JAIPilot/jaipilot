type JsonRpcId = string | number | null;

type JsonRpcRequest = {
  jsonrpc?: string;
  id?: JsonRpcId;
  method?: string;
  params?: Record<string, unknown>;
};

type JsonRpcResponse = {
  jsonrpc: "2.0";
  id: JsonRpcId;
  result?: unknown;
  error?: { code: number; message: string };
};

type CloudCaller = (action: string, input: Record<string, unknown>) => Promise<unknown>;

type Tool = {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  annotations: {
    readOnlyHint: boolean;
    destructiveHint: boolean;
    idempotentHint: boolean;
    openWorldHint: boolean;
  };
};

const TOOLS: Tool[] = [
  {
    name: "workspace_create",
    description:
      "Create an isolated Java workspace from an exact committed GitHub SHA. JDK 17, 21, and 25 plus Maven 3.9.16 and Gradle 9.7.0 are preinstalled; repository wrappers remain preferred. The JAIPilot GitHub App must be installed. Use remote hardware only when it materially helps; uncommitted local changes are not uploaded.",
    inputSchema: {
      type: "object",
      additionalProperties: false,
      required: ["repository", "commit_sha"],
      properties: {
        repository: { type: "string", description: "GitHub repository in owner/name form." },
        commit_sha: {
          type: "string",
          pattern: "^[0-9a-f]{40}$",
          description: "Exact lowercase 40-character commit SHA to mount.",
        },
        profile: {
          type: "string",
          enum: ["small", "medium", "large"],
          default: "medium",
          description:
            "Bounded Java hardware profile. Medium (2 CPU, 4 GiB) is the default for normal builds.",
        },
        ttl_minutes: {
          type: "integer",
          minimum: 15,
          maximum: 360,
          default: 180,
          description: "Hard wall-clock lifetime; the workspace is destroyed when it expires.",
        },
      },
    },
    annotations: toolAnnotations(false, false, false),
  },
  {
    name: "process_start",
    description:
      "Start one asynchronous shell command in an existing JAIPilot Java workspace. JAIPilot selects JDK 17, 21, or 25 from repository metadata unless the command overrides JAVA_HOME or JAIPILOT_JAVA_VERSION. Maven and Gradle caches are reused for the life of the workspace. The command returns durable session and command IDs and is killed at the requested timeout.",
    inputSchema: {
      type: "object",
      additionalProperties: false,
      required: ["workspace_id", "command"],
      properties: {
        workspace_id: { type: "string", description: "Workspace ID from workspace_create." },
        command: {
          type: "string",
          minLength: 1,
          maxLength: 32768,
          description: "Repository command selected by the customer coding agent.",
        },
        cwd: {
          type: "string",
          default: ".",
          description: "Relative path inside the checked-out repository.",
        },
        timeout_seconds: {
          type: "integer",
          minimum: 1,
          maximum: 14400,
          default: 3600,
          description: "Hard process timeout in seconds.",
        },
      },
    },
    annotations: toolAnnotations(false, true, false),
  },
  {
    name: "process_status",
    description: "Read the durable running state and final exit code of a remote process.",
    inputSchema: processIdentitySchema(true),
    annotations: toolAnnotations(true, false, true),
  },
  {
    name: "process_logs",
    description:
      "Read the latest remote process logs. At most the final 200 KB is returned and truncated=true identifies a bounded tail.",
    inputSchema: processIdentitySchema(true),
    annotations: toolAnnotations(true, false, true),
  },
  {
    name: "process_cancel",
    description:
      "Terminate and remove a remote process session. Repeating cancellation is safe and reports whether a live session was removed.",
    inputSchema: processIdentitySchema(false),
    annotations: toolAnnotations(false, true, true),
  },
  {
    name: "workspace_destroy",
    description:
      "Permanently destroy one JAIPilot remote workspace and all of its processes and files. Call this as soon as remote work is complete.",
    inputSchema: {
      type: "object",
      additionalProperties: false,
      required: ["workspace_id"],
      properties: {
        workspace_id: { type: "string", description: "Workspace ID from workspace_create." },
      },
    },
    annotations: toolAnnotations(false, true, true),
  },
];

export async function handleMcpRequest(
  request: JsonRpcRequest,
  cloud: CloudCaller = callCloud,
): Promise<JsonRpcResponse | undefined> {
  if (request.jsonrpc !== "2.0" || typeof request.method !== "string") {
    return rpcError(request.id ?? null, -32600, "Invalid Request");
  }
  if (request.method.startsWith("notifications/")) return undefined;
  const id = request.id ?? null;
  switch (request.method) {
    case "initialize":
      return rpcResult(id, {
        protocolVersion: "2025-03-26",
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: "JAIPilot Remote Execution", version: "0.2.0" },
        instructions:
          "JAIPilot supplies disposable Java remote execution, not another reasoning agent. Create a workspace only from the exact committed GitHub SHA; local dirty files are never included. JDK 17/21/25, Maven 3.9.16, and Gradle 9.7.0 are preinstalled, while repository wrappers remain preferred. Reuse one workspace for related commands and dependency caches, poll asynchronous status, inspect bounded logs, and always destroy the workspace. Never claim remote changes were synchronized locally or pushed.",
      });
    case "ping":
      return rpcResult(id, {});
    case "tools/list":
      return rpcResult(id, { tools: TOOLS });
    case "tools/call":
      return rpcResult(id, await callTool(request.params, cloud));
    default:
      return rpcError(id, -32601, "Method not found");
  }
}

async function callTool(
  params: Record<string, unknown> | undefined,
  cloud: CloudCaller,
): Promise<{ content: Array<{ type: "text"; text: string }>; isError?: boolean }> {
  const name = params?.name;
  const args = params?.arguments;
  if (typeof name !== "string" || !args || typeof args !== "object" || Array.isArray(args)) {
    return toolError("tools/call requires a tool name and object arguments");
  }
  try {
    const action = TOOLS.find((tool) => tool.name === name)?.name;
    if (!action) return toolError(`Unknown tool: ${name}`);
    const result = await cloud(action, cloudInput(name, args as Record<string, unknown>));
    return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
  } catch (error) {
    return toolError(error instanceof Error ? error.message : "JAIPilot remote operation failed");
  }
}

export function cloudInput(
  tool: string,
  args: Record<string, unknown>,
): Record<string, unknown> {
  switch (tool) {
    case "workspace_create":
      return {
        repository: args.repository,
        commitSha: args.commit_sha,
        profile: args.profile,
        ttlMinutes: args.ttl_minutes,
      };
    case "process_start":
      return {
        workspaceId: args.workspace_id,
        command: args.command,
        cwd: args.cwd,
        timeoutSeconds: args.timeout_seconds,
      };
    case "process_status":
    case "process_logs":
      return {
        workspaceId: args.workspace_id,
        processSessionId: args.session_id,
        commandId: args.command_id,
      };
    case "process_cancel":
      return { workspaceId: args.workspace_id, processSessionId: args.session_id };
    case "workspace_destroy":
      return { workspaceId: args.workspace_id };
    default:
      throw new Error(`Unknown tool: ${tool}`);
  }
}

async function callCloud(action: string, input: Record<string, unknown>): Promise<unknown> {
  const apiUrl = required("JAIPILOT_CLOUD_API_URL");
  const triggerSecret = required("JAIPILOT_CLOUD_TRIGGER_SECRET");
  const response = await fetch(apiUrl, {
    method: "POST",
    headers: {
      authorization: `Bearer ${triggerSecret}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ action, ...input }),
  });
  const body = await response.json().catch(() => ({
    error: "JAIPilot Cloud returned invalid JSON",
  }));
  if (!response.ok) {
    const message = body && typeof body === "object" && "error" in body
      ? String(body.error)
      : `JAIPilot Cloud HTTP ${response.status}`;
    throw new Error(message);
  }
  return body;
}

async function runStdioServer(): Promise<void> {
  const decoder = new TextDecoder();
  const encoder = new TextEncoder();
  let buffer = "";
  for await (const chunk of Deno.stdin.readable) {
    buffer += decoder.decode(chunk, { stream: true });
    let newline;
    while ((newline = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, newline).trim();
      buffer = buffer.slice(newline + 1);
      if (line) await respondToLine(line, encoder);
    }
  }
  const remaining = buffer.trim();
  if (remaining) await respondToLine(remaining, encoder);
}

async function respondToLine(line: string, encoder: TextEncoder): Promise<void> {
  let response: JsonRpcResponse | undefined;
  try {
    response = await handleMcpRequest(JSON.parse(line) as JsonRpcRequest);
  } catch {
    response = rpcError(null, -32700, "Parse error");
  }
  if (response) await Deno.stdout.write(encoder.encode(`${JSON.stringify(response)}\n`));
}

function processIdentitySchema(includeCommand: boolean): Record<string, unknown> {
  const required = ["workspace_id", "session_id"];
  const properties: Record<string, unknown> = {
    workspace_id: { type: "string", description: "Workspace ID from workspace_create." },
    session_id: { type: "string", description: "Process session ID from process_start." },
  };
  if (includeCommand) {
    required.push("command_id");
    properties.command_id = { type: "string", description: "Command ID from process_start." };
  }
  return { type: "object", additionalProperties: false, required, properties };
}

function toolAnnotations(readOnly: boolean, destructive: boolean, idempotent: boolean) {
  return {
    readOnlyHint: readOnly,
    destructiveHint: destructive,
    idempotentHint: idempotent,
    openWorldHint: true,
  };
}

function toolError(message: string) {
  return { content: [{ type: "text" as const, text: message }], isError: true };
}

function rpcResult(id: JsonRpcId, result: unknown): JsonRpcResponse {
  return { jsonrpc: "2.0", id, result };
}

function rpcError(id: JsonRpcId, code: number, message: string): JsonRpcResponse {
  return { jsonrpc: "2.0", id, error: { code, message } };
}

function required(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

if (import.meta.main) {
  await runStdioServer();
}
