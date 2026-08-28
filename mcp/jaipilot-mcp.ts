import { MCP_SKILL_BUNDLE, type McpSkillSource } from "./skill_sources.ts";

const SKILL_PAGE_SIZE = 5;
const SKILL_URI_PREFIX = "skill://jaipilot/";
const CURSOR_PREFIX = "jaipilot-skills:";
const REMOTE_MCP_URL = "https://api.jaipilot.com/functions/v1/jaipilot-cloud/mcp";
const AUTHORIZATION_SERVER = "https://otxfylhjrlaesjagfhfi.supabase.co/auth/v1";
const MAX_HTTP_REQUEST_BYTES = 256 * 1024;
const JSON_HEADERS = {
  "cache-control": "no-store",
  "content-type": "application/json; charset=utf-8",
};

type JsonRpcId = string | number | null;

export type JsonRpcRequest = {
  jsonrpc?: string;
  id?: JsonRpcId;
  method?: string;
  params?: unknown;
};

export type JsonRpcResponse = {
  jsonrpc: "2.0";
  id: JsonRpcId;
  result?: unknown;
  error?: { code: number; message: string };
};

export type SkillEntry = {
  uri: string;
  frontmatter: { name: string; description: string };
  resources: Array<{ uri: string; digest: `sha256:${string}` }>;
};

type Fetcher = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

export function handleMcpRequest(request: JsonRpcRequest): JsonRpcResponse | undefined {
  if (request.jsonrpc !== "2.0" || typeof request.method !== "string") {
    return rpcError(request.id ?? null, -32600, "Invalid Request");
  }
  if (request.method.startsWith("notifications/")) return undefined;
  const id = request.id ?? null;
  switch (request.method) {
    case "initialize":
      return rpcResult(id, {
        protocolVersion: "2025-03-26",
        capabilities: {
          tools: { listChanged: false },
          extensions: { "io.modelcontextprotocol/skills": {} },
        },
        serverInfo: { name: "JAIPilot", version: MCP_SKILL_BUNDLE.version },
        instructions:
          "JAIPilot supplies eight versioned Java engineering skills and bounded remote execution while the host agent remains the only planner and editor. Load only the skill resources needed for the task. Remote tool calls use the existing OAuth service, upload source only after explicit repository-specific consent, and must destroy every workspace after use.",
      });
    case "ping":
      return rpcResult(id, {});
    case "skills/list":
      return protocolResult(id, request.params, listSkills);
    case "skills/get":
      return protocolResult(id, request.params, getSkill);
    case "resources/read":
      return protocolResult(id, request.params, readResource);
    case "tools/list":
    case "tools/call":
      return rpcError(id, -32603, "Remote tool request was not forwarded by the HTTP transport");
    default:
      return rpcError(id, -32601, "Method not found");
  }
}

export async function handleMcpHttpRequest(
  request: Request,
  upstreamFetch: Fetcher = fetch,
): Promise<Response> {
  if (request.method === "GET") {
    if (new URL(request.url).pathname.endsWith("/.well-known/oauth-protected-resource")) {
      return json({
        resource: publicMcpUrl(request),
        authorization_servers: [AUTHORIZATION_SERVER],
        scopes_supported: ["email"],
        bearer_methods_supported: ["header"],
        resource_documentation: "https://github.com/JAIPilot/jaipilot#remote-build-beta",
      });
    }
    return json({ service: "jaipilot", version: MCP_SKILL_BUNDLE.version, status: "ok" });
  }
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const declaredLength = Number(request.headers.get("content-length"));
  if (Number.isFinite(declaredLength) && declaredLength > MAX_HTTP_REQUEST_BYTES) {
    return json({ error: "Request too large" }, 413);
  }

  let body: string;
  let rpcRequest: JsonRpcRequest;
  try {
    body = await request.text();
    if (new TextEncoder().encode(body).byteLength > MAX_HTTP_REQUEST_BYTES) {
      return json({ error: "Request too large" }, 413);
    }
    rpcRequest = JSON.parse(body);
  } catch {
    return json(rpcError(null, -32700, "Parse error"), 400);
  }

  if (rpcRequest.method === "tools/list" || rpcRequest.method === "tools/call") {
    return forwardRemoteTools(request, body, upstreamFetch);
  }

  const response = handleMcpRequest(rpcRequest);
  return response ? json(response) : new Response(null, { status: 202 });
}

function listSkills(input: Record<string, unknown>): { skills: SkillEntry[]; nextCursor?: string } {
  rejectUnexpected(input, ["cursor"], "skills/list");
  const start = listStart(input.cursor);
  const end = Math.min(start + SKILL_PAGE_SIZE, MCP_SKILL_BUNDLE.skills.length);
  const result: { skills: SkillEntry[]; nextCursor?: string } = {
    skills: MCP_SKILL_BUNDLE.skills.slice(start, end).map(skillEntry),
  };
  if (end < MCP_SKILL_BUNDLE.skills.length) result.nextCursor = `${CURSOR_PREFIX}${end}`;
  return result;
}

function getSkill(input: Record<string, unknown>): { skill: SkillEntry } {
  rejectUnexpected(input, ["uri"], "skills/get");
  if (typeof input.uri !== "string") throw new Error("skills/get requires a skill URI");
  const skill = MCP_SKILL_BUNDLE.skills.find(
    (candidate) => skillUri(candidate.name, "SKILL.md") === input.uri,
  );
  if (!skill) throw new Error("Unknown JAIPilot skill URI");
  return { skill: skillEntry(skill) };
}

function readResource(input: Record<string, unknown>): {
  contents: Array<{ uri: string; mimeType: "text/markdown"; text: string }>;
} {
  rejectUnexpected(input, ["uri"], "resources/read");
  if (typeof input.uri !== "string") throw new Error("resources/read requires a resource URI");
  for (const skill of MCP_SKILL_BUNDLE.skills) {
    const file = skill.files.find((candidate) =>
      skillUri(skill.name, candidate.path) === input.uri
    );
    if (file) {
      return {
        contents: [{ uri: input.uri, mimeType: "text/markdown", text: file.content }],
      };
    }
  }
  throw new Error("Unknown JAIPilot skill resource URI");
}

function protocolResult(
  id: JsonRpcId,
  params: unknown,
  method: (input: Record<string, unknown>) => unknown,
): JsonRpcResponse {
  try {
    if (params !== undefined && (!params || typeof params !== "object" || Array.isArray(params))) {
      throw new Error("Method parameters must be an object");
    }
    return rpcResult(id, method((params ?? {}) as Record<string, unknown>));
  } catch (error) {
    return rpcError(
      id,
      -32602,
      error instanceof Error ? error.message : "Invalid method parameters",
    );
  }
}

function skillEntry(skill: McpSkillSource): SkillEntry {
  return {
    uri: skillUri(skill.name, "SKILL.md"),
    frontmatter: { name: skill.name, description: skill.description },
    resources: skill.files.map((file) => ({
      uri: skillUri(skill.name, file.path),
      digest: file.digest,
    })),
  };
}

function skillUri(name: string, path: string): string {
  return `${SKILL_URI_PREFIX}${name}/${path}`;
}

function listStart(cursor: unknown): number {
  if (cursor === undefined) return 0;
  if (typeof cursor !== "string" || !cursor.startsWith(CURSOR_PREFIX)) {
    throw new Error("Invalid skills/list cursor");
  }
  const start = Number(cursor.slice(CURSOR_PREFIX.length));
  if (
    !Number.isSafeInteger(start) || start <= 0 || start >= MCP_SKILL_BUNDLE.skills.length ||
    start % SKILL_PAGE_SIZE !== 0
  ) {
    throw new Error("Invalid skills/list cursor");
  }
  return start;
}

function rejectUnexpected(
  input: Record<string, unknown>,
  accepted: string[],
  method: string,
): void {
  const unexpected = Object.keys(input).filter((key) => !accepted.includes(key) && key !== "_meta");
  if (unexpected.length > 0) throw new Error(`${method} does not accept: ${unexpected.join(", ")}`);
}

async function forwardRemoteTools(
  request: Request,
  body: string,
  upstreamFetch: Fetcher,
): Promise<Response> {
  const headers = new Headers({
    accept: request.headers.get("accept") ?? "application/json, text/event-stream",
    "content-type": "application/json",
  });
  const authorization = request.headers.get("authorization");
  if (authorization) headers.set("authorization", authorization);
  for (const name of ["mcp-protocol-version", "mcp-session-id"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const upstream = await upstreamFetch(REMOTE_MCP_URL, { method: "POST", headers, body });
  const responseHeaders = new Headers({ "cache-control": "no-store" });
  for (const name of ["content-type", "www-authenticate", "mcp-session-id", "retry-after"]) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  if (upstream.status === 401 && responseHeaders.has("www-authenticate")) {
    responseHeaders.set(
      "www-authenticate",
      `Bearer resource_metadata="${
        publicMcpUrl(request).replace(
          /\/mcp$/,
          "/.well-known/oauth-protected-resource",
        )
      }", scope="email"`,
    );
  }
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

function publicMcpUrl(request: Request): string {
  const url = new URL(request.url);
  const functionRoot = url.pathname.match(/^(.*\/functions\/v1\/jaipilot)(?:\/.*)?$/)?.[1];
  if (!functionRoot) throw new Error("JAIPilot MCP requires its canonical function path");
  return `${url.origin}${functionRoot}/mcp`;
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: JSON_HEADERS });
}

function rpcResult(id: JsonRpcId, result: unknown): JsonRpcResponse {
  return { jsonrpc: "2.0", id, result };
}

function rpcError(id: JsonRpcId, code: number, message: string): JsonRpcResponse {
  return { jsonrpc: "2.0", id, error: { code, message } };
}
