import assert from "node:assert/strict";
import {
  handleMcpHttpRequest,
  handleMcpRequest,
  type JsonRpcResponse,
  type SkillEntry,
} from "../mcp/jaipilot-mcp.ts";

const REMOTE_MCP_URL = "https://api.jaipilot.com/functions/v1/jaipilot-cloud/mcp";
const PUBLIC_MCP_URL = "https://api.jaipilot.com/functions/v1/jaipilot/mcp";

Deno.test("JAIPilot advertises the standard MCP Skills extension", () => {
  const response = handleMcpRequest({
    jsonrpc: "2.0",
    id: "initialize",
    method: "initialize",
    params: { protocolVersion: "2025-03-26" },
  });
  assert.deepEqual(
    (response?.result as {
      capabilities: { extensions: Record<string, unknown> };
    }).capabilities.extensions,
    { "io.modelcontextprotocol/skills": {} },
  );
  assert.equal(
    (response?.result as { serverInfo: { version: string } }).serverInfo.version,
    "6.8.0",
  );
});

Deno.test("Skills list, get, and read return all exact digest-verified resources", async () => {
  const first = result<{ skills: SkillEntry[]; nextCursor: string }>(handleMcpRequest({
    jsonrpc: "2.0",
    id: 1,
    method: "skills/list",
    params: {},
  }));
  assert.equal(first.skills.length, 5);
  assert.equal(first.nextCursor, "jaipilot-skills:5");

  const second = result<{ skills: SkillEntry[]; nextCursor?: string }>(handleMcpRequest({
    jsonrpc: "2.0",
    id: 2,
    method: "skills/list",
    params: { cursor: first.nextCursor },
  }));
  assert.equal(second.skills.length, 3);
  assert.equal(second.nextCursor, undefined);

  const skills = [...first.skills, ...second.skills];
  assert.deepEqual(skills.map((skill) => skill.frontmatter.name), [
    "jaipilot-clean-java",
    "jaipilot-fast-execution",
    "jaipilot-generate-tests",
    "jaipilot-maintainer-intent",
    "jaipilot-openrewrite",
    "jaipilot-optimize-java",
    "jaipilot-remote-java",
    "jaipilot-review-diff",
  ]);

  for (const skill of skills) {
    assert.equal(skill.uri, skill.resources[0].uri);
    assert.match(skill.uri, new RegExp(`^skill://jaipilot/${skill.frontmatter.name}/SKILL\\.md$`));
    const selected = result<{ skill: SkillEntry }>(handleMcpRequest({
      jsonrpc: "2.0",
      id: "get",
      method: "skills/get",
      params: { uri: skill.uri },
    }));
    assert.deepEqual(selected.skill, skill);

    for (const resource of skill.resources) {
      assert.match(resource.digest, /^sha256:[0-9a-f]{64}$/);
      const read = result<{
        contents: Array<{ uri: string; mimeType: string; text: string }>;
      }>(handleMcpRequest({
        jsonrpc: "2.0",
        id: "read",
        method: "resources/read",
        params: { uri: resource.uri },
      }));
      assert.equal(read.contents.length, 1);
      assert.equal(read.contents[0].uri, resource.uri);
      assert.equal(read.contents[0].mimeType, "text/markdown");
      assert.equal(resource.digest, `sha256:${await sha256(read.contents[0].text)}`);

      if (resource.uri === skill.uri) {
        const frontmatter = read.contents[0].text.match(
          /^---\nname: ([a-z0-9-]+)\ndescription: ([^\n]+)\n---\n/,
        );
        assert.ok(frontmatter);
        assert.deepEqual(skill.frontmatter, {
          name: frontmatter[1],
          description: frontmatter[2],
        });
      }
    }
  }
});

Deno.test("Skills methods fail closed on invalid cursors and unlisted URIs", () => {
  for (
    const request of [
      { method: "skills/list", params: { cursor: "5" } },
      { method: "skills/get", params: { uri: "skill://jaipilot/../SKILL.md" } },
      {
        method: "resources/read",
        params: { uri: "skill://jaipilot/jaipilot-clean-java/references/missing.md" },
      },
      { method: "resources/read", params: { uri: "file:///etc/passwd" } },
    ]
  ) {
    const response = handleMcpRequest({ jsonrpc: "2.0", id: "invalid", ...request });
    assert.equal(response?.error?.code, -32602);
  }
});

Deno.test("HTTP skill reads stay local and do not contact remote execution", async () => {
  let upstreamCalled = false;
  const response = await handleMcpHttpRequest(
    request(
      { jsonrpc: "2.0", id: "skills", method: "skills/list", params: {} },
      "Bearer public-skill-reader",
    ),
    () => {
      upstreamCalled = true;
      return Promise.reject(new Error("must not be called"));
    },
  );
  assert.equal(response.status, 200);
  assert.equal(upstreamCalled, false);
  const body = await response.json();
  assert.equal(body.result.skills.length, 5);
});

Deno.test("HTTP MCP initialization starts the Codex OAuth handshake", async () => {
  const response = await handleMcpHttpRequest(
    request({ jsonrpc: "2.0", id: "initialize", method: "initialize", params: {} }),
  );
  assert.equal(response.status, 401);
  assert.equal(
    response.headers.get("www-authenticate"),
    'Bearer resource_metadata="https://api.jaipilot.com/functions/v1/jaipilot/.well-known/oauth-protected-resource", scope="email"',
  );
});

Deno.test("HTTP transport publishes OAuth metadata for the combined public resource", async () => {
  for (const path of ["/mcp", "/.well-known/oauth-protected-resource"]) {
    const response = await handleMcpHttpRequest(
      new Request(`http://api.jaipilot.com${path}`),
    );
    assert.equal(response.status, 200);
    const metadata = await response.json();
    assert.deepEqual(metadata, {
      resource: PUBLIC_MCP_URL,
      authorization_servers: ["https://otxfylhjrlaesjagfhfi.supabase.co/auth/v1"],
      scopes_supported: ["email"],
      bearer_methods_supported: ["header"],
      resource_documentation: "https://github.com/JAIPilot/jaipilot#remote-build-beta",
    });
  }
});

Deno.test("HTTP tool methods preserve OAuth while forwarding no cookies or browser origin", async () => {
  const challenge =
    'Bearer resource_metadata="https://api.jaipilot.com/functions/v1/jaipilot-cloud/.well-known/oauth-protected-resource"';
  let forwarded = false;
  const response = await handleMcpHttpRequest(
    new Request("https://api.jaipilot.com/functions/v1/jaipilot/mcp", {
      method: "POST",
      headers: {
        accept: "application/json, text/event-stream",
        authorization: "Bearer opaque-user-token",
        "content-type": "application/json",
        cookie: "must-not-forward=true",
        "mcp-protocol-version": "2025-03-26",
        origin: "https://untrusted.example",
      },
      body: JSON.stringify({ jsonrpc: "2.0", id: "tools", method: "tools/list", params: {} }),
    }),
    (input, init) => {
      forwarded = true;
      assert.equal(input, REMOTE_MCP_URL);
      assert.equal(init?.method, "POST");
      const headers = new Headers(init?.headers);
      assert.equal(headers.get("authorization"), "Bearer opaque-user-token");
      assert.equal(headers.get("mcp-protocol-version"), "2025-03-26");
      assert.equal(headers.get("cookie"), null);
      assert.equal(headers.get("origin"), null);
      return Promise.resolve(
        new Response(JSON.stringify({ error: "Unauthorized" }), {
          status: 401,
          headers: { "content-type": "application/json", "www-authenticate": challenge },
        }),
      );
    },
  );
  assert.equal(forwarded, true);
  assert.equal(response.status, 401);
  assert.equal(
    response.headers.get("www-authenticate"),
    'Bearer resource_metadata="https://api.jaipilot.com/functions/v1/jaipilot/.well-known/oauth-protected-resource", scope="email"',
  );
});

Deno.test("HTTP transport rejects oversized requests before forwarding", async () => {
  let forwarded = false;
  const response = await handleMcpHttpRequest(
    new Request("https://api.jaipilot.com/functions/v1/jaipilot/mcp", {
      method: "POST",
      headers: { "content-length": String(256 * 1024 + 1) },
      body: "{}",
    }),
    () => {
      forwarded = true;
      return Promise.reject(new Error("must not be called"));
    },
  );
  assert.equal(response.status, 413);
  assert.equal(forwarded, false);
});

function result<T>(response: JsonRpcResponse | undefined): T {
  assert.ok(response);
  assert.equal(response.error, undefined);
  return response.result as T;
}

function request(body: unknown, authorization?: string): Request {
  const headers = new Headers({ "content-type": "application/json" });
  if (authorization) headers.set("authorization", authorization);
  return new Request("https://api.jaipilot.com/functions/v1/jaipilot/mcp", {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}
