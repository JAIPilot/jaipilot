import { MCP_SKILL_BUNDLE, type McpSkillSource } from "./skill_sources.ts";

const SKILL_PAGE_SIZE = 5;
const SKILL_URI_PREFIX = "skill://jaipilot/";
const CURSOR_PREFIX = "jaipilot-skills:";
const RESOURCE_CURSOR_PREFIX = "jaipilot-resources:";
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

type SkillResource = {
  uri: string;
  name: string;
  description: string;
  mimeType: "mcp/skill";
  _meta: {
    skill_id: string;
    skill_name: string;
    source: "user";
    allow_implicit_invocation: true;
  };
};

const SKILL_GET_TOOL = {
  name: "skill_get",
  title: "Get Java Skill",
  description:
    "Lists JAIPilot's eight versioned Java skills, or returns the exact current instructions and references for one named skill. This read-only fallback uploads no source and starts no compute.",
  inputSchema: {
    type: "object",
    additionalProperties: false,
    properties: {
      name: {
        type: "string",
        enum: MCP_SKILL_BUNDLE.skills.map((skill) => skill.name),
        description: "Optional exact skill name. Omit it to list compact metadata for all skills.",
      },
    },
  },
  annotations: {
    title: "Get Java Skill",
    readOnlyHint: true,
    destructiveHint: false,
    idempotentHint: true,
    openWorldHint: false,
  },
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
          resources: { subscribe: false, listChanged: false },
          extensions: { "io.modelcontextprotocol/skills": {} },
        },
        serverInfo: { name: "JAIPilot", version: MCP_SKILL_BUNDLE.version },
        instructions:
          "JAIPilot supplies eight versioned Java engineering skills and bounded remote execution while the host agent remains the only planner and editor. Discover skills through the Skills extension or mcp/skill resources. If the client does not yet promote server skills, use the read-only skill_get tool to load only the exact skill needed. Remote tool calls use the existing OAuth service, upload only an explicitly approved exact committed archive, and run as independent builds that terminate automatically.",
      });
    case "ping":
      return rpcResult(id, {});
    case "skills/list":
      return protocolResult(id, request.params, listSkills);
    case "skills/get":
      return protocolResult(id, request.params, getSkill);
    case "resources/list":
      return protocolResult(id, request.params, listResources);
    case "resources/templates/list":
      return protocolResult(id, request.params, listResourceTemplates);
    case "resources/read":
      return protocolResult(id, request.params, readResource);
    case "tools/list":
      return rpcError(id, -32603, "Remote tool request was not forwarded by the HTTP transport");
    case "tools/call": {
      const local = localSkillToolCall(id, request.params);
      return local ?? rpcError(
        id,
        -32603,
        "Remote tool request was not forwarded by the HTTP transport",
      );
    }
    default:
      return rpcError(id, -32601, "Method not found");
  }
}

export async function handleMcpHttpRequest(
  request: Request,
  upstreamFetch: Fetcher = fetch,
): Promise<Response> {
  if (request.method === "GET") {
    const pathname = new URL(request.url).pathname;
    if (pathname.endsWith("/mcp") || pathname.endsWith("/.well-known/oauth-protected-resource")) {
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
  if (!/^Bearer \S+$/.test(request.headers.get("authorization") ?? "")) {
    return new Response(JSON.stringify({ error: "Missing OAuth access token" }), {
      status: 401,
      headers: {
        ...JSON_HEADERS,
        "www-authenticate": oauthChallenge(request),
      },
    });
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

  if (rpcRequest.method === "tools/call") {
    const local = localSkillToolCall(rpcRequest.id ?? null, rpcRequest.params);
    if (local) return json(local);
  }
  if (rpcRequest.method === "tools/list" || rpcRequest.method === "tools/call") {
    return forwardRemoteTools(request, body, rpcRequest.method, upstreamFetch);
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

function listResources(
  input: Record<string, unknown>,
): { resources: SkillResource[]; nextCursor?: string } {
  rejectUnexpected(input, ["cursor"], "resources/list");
  const start = listStart(input.cursor, RESOURCE_CURSOR_PREFIX, "resources/list");
  const end = Math.min(start + SKILL_PAGE_SIZE, MCP_SKILL_BUNDLE.skills.length);
  const result: { resources: SkillResource[]; nextCursor?: string } = {
    resources: MCP_SKILL_BUNDLE.skills.slice(start, end).map(skillResource),
  };
  if (end < MCP_SKILL_BUNDLE.skills.length) {
    result.nextCursor = `${RESOURCE_CURSOR_PREFIX}${end}`;
  }
  return result;
}

function listResourceTemplates(input: Record<string, unknown>): { resourceTemplates: never[] } {
  rejectUnexpected(input, ["cursor"], "resources/templates/list");
  if (input.cursor !== undefined) throw new Error("Invalid resources/templates/list cursor");
  return { resourceTemplates: [] };
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

function skillResource(skill: McpSkillSource): SkillResource {
  return {
    uri: `${SKILL_URI_PREFIX}${skill.name}`,
    name: skill.name,
    description: skill.description,
    mimeType: "mcp/skill",
    _meta: {
      skill_id: `jaipilot/${skill.name}`,
      skill_name: skill.name,
      source: "user",
      allow_implicit_invocation: true,
    },
  };
}

function skillUri(name: string, path: string): string {
  return `${SKILL_URI_PREFIX}${name}/${path}`;
}

function listStart(
  cursor: unknown,
  prefix = CURSOR_PREFIX,
  method = "skills/list",
): number {
  if (cursor === undefined) return 0;
  if (typeof cursor !== "string" || !cursor.startsWith(prefix)) {
    throw new Error(`Invalid ${method} cursor`);
  }
  const start = Number(cursor.slice(prefix.length));
  if (
    !Number.isSafeInteger(start) || start <= 0 || start >= MCP_SKILL_BUNDLE.skills.length ||
    start % SKILL_PAGE_SIZE !== 0
  ) {
    throw new Error(`Invalid ${method} cursor`);
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
  method: string,
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
    responseHeaders.set("www-authenticate", oauthChallenge(request));
  }
  let responseBody: BodyInit | null = upstream.body;
  if (
    method === "tools/list" && upstream.ok &&
    (upstream.headers.get("content-type") ?? "").includes("application/json")
  ) {
    const text = await upstream.text();
    responseBody = withCurrentSkillTool(text);
  }
  return new Response(responseBody, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

function withCurrentSkillTool(body: string): string {
  try {
    const message = JSON.parse(body) as {
      result?: { tools?: Array<{ name?: string }> };
    };
    const tools = message.result?.tools;
    if (!Array.isArray(tools)) return body;
    message.result!.tools = [
      SKILL_GET_TOOL,
      ...tools.filter((tool) => tool.name !== SKILL_GET_TOOL.name),
    ];
    return JSON.stringify(message);
  } catch {
    return body;
  }
}

function localSkillToolCall(id: JsonRpcId, params: unknown): JsonRpcResponse | undefined {
  if (!params || typeof params !== "object" || Array.isArray(params)) return undefined;
  const input = params as Record<string, unknown>;
  if (input.name !== SKILL_GET_TOOL.name) return undefined;
  const args = input.arguments;
  if (!args || typeof args !== "object" || Array.isArray(args)) {
    return rpcResult(id, toolError("skill_get requires object arguments"));
  }
  try {
    return rpcResult(id, toolResult(skillToolResponse(args as Record<string, unknown>)));
  } catch (error) {
    return rpcResult(
      id,
      toolError(error instanceof Error ? error.message : "Invalid skill_get arguments"),
    );
  }
}

function skillToolResponse(input: Record<string, unknown>): Record<string, unknown> {
  rejectUnexpected(input, ["name"], "skill_get");
  const selected = input.name;
  if (selected === undefined) {
    return {
      schemaVersion: 1,
      version: MCP_SKILL_BUNDLE.version,
      sourceRepository: "https://github.com/JAIPilot/jaipilot",
      skills: MCP_SKILL_BUNDLE.skills.map((skill) => ({
        name: skill.name,
        description: skill.description,
        uri: skillUri(skill.name, "SKILL.md"),
        files: skill.files.map((file) => ({
          path: file.path,
          uri: skillUri(skill.name, file.path),
          digest: file.digest,
        })),
      })),
    };
  }
  if (typeof selected !== "string") throw new Error("skill_get name must be a string");
  const skill = MCP_SKILL_BUNDLE.skills.find((candidate) => candidate.name === selected);
  if (!skill) {
    throw new Error(
      `Unknown JAIPilot skill. Choose one of: ${
        MCP_SKILL_BUNDLE.skills.map((candidate) => candidate.name).join(", ")
      }`,
    );
  }
  return {
    schemaVersion: 1,
    version: MCP_SKILL_BUNDLE.version,
    sourceRepository: "https://github.com/JAIPilot/jaipilot",
    skill: {
      name: skill.name,
      description: skill.description,
      uri: skillUri(skill.name, "SKILL.md"),
      files: skill.files.map((file) => ({
        path: file.path,
        uri: skillUri(skill.name, file.path),
        digest: file.digest,
        content: file.content,
      })),
    },
  };
}

function toolResult(value: unknown): { content: Array<{ type: "text"; text: string }> } {
  return { content: [{ type: "text", text: JSON.stringify(value) }] };
}

function toolError(message: string): {
  content: Array<{ type: "text"; text: string }>;
  isError: true;
} {
  return { content: [{ type: "text", text: message }], isError: true };
}

function oauthChallenge(request: Request): string {
  const metadata = publicMcpUrl(request).replace(
    /\/mcp$/,
    "/.well-known/oauth-protected-resource",
  );
  return `Bearer resource_metadata="${metadata}", scope="email"`;
}

function publicMcpUrl(request: Request): string {
  const url = new URL(request.url);
  const local = url.hostname === "localhost" || url.hostname === "127.0.0.1";
  const origin = local ? url.origin : `https://${url.host}`;
  return `${origin}/functions/v1/jaipilot/mcp`;
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
