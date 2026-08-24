import assert from "node:assert/strict";

import { cloudInput, handleMcpRequest } from "../plugins/jaipilot/mcp/jaipilot-mcp.ts";

Deno.test("negotiates MCP and exposes six bounded remote tools", async () => {
  const initialized = await handleMcpRequest({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {},
  });
  assert.deepEqual(initialized, {
    jsonrpc: "2.0",
    id: 1,
    result: {
      protocolVersion: "2025-03-26",
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: "JAIPilot Remote Execution", version: "0.2.0" },
      instructions:
        "JAIPilot supplies disposable Java remote execution, not another reasoning agent. Create a workspace only from the exact committed GitHub SHA; local dirty files are never included. JDK 17/21/25, Maven 3.9.16, and Gradle 9.7.0 are preinstalled, while repository wrappers remain preferred. Reuse one workspace for related commands and dependency caches, poll asynchronous status, inspect bounded logs, and always destroy the workspace. Never claim remote changes were synchronized locally or pushed.",
    },
  });

  const listed = await handleMcpRequest({ jsonrpc: "2.0", id: 2, method: "tools/list" });
  const tools = (listed?.result as {
    tools: Array<{
      name: string;
      annotations: {
        readOnlyHint: boolean;
        destructiveHint: boolean;
        idempotentHint: boolean;
        openWorldHint: boolean;
      };
    }>;
  }).tools;
  assert.deepEqual(tools.map((tool) => tool.name), [
    "workspace_create",
    "process_start",
    "process_status",
    "process_logs",
    "process_cancel",
    "workspace_destroy",
  ]);
  assert.deepEqual(tools[0].annotations, {
    readOnlyHint: false,
    destructiveHint: false,
    idempotentHint: false,
    openWorldHint: true,
  });
  assert.equal(tools[2].annotations.readOnlyHint, true);
  assert.equal(tools[5].annotations.destructiveHint, true);
});

Deno.test("maps MCP snake case arguments to the authenticated Cloud protocol", async () => {
  const calls: Array<{ action: string; input: Record<string, unknown> }> = [];
  const response = await handleMcpRequest(
    {
      jsonrpc: "2.0",
      id: 7,
      method: "tools/call",
      params: {
        name: "process_start",
        arguments: {
          workspace_id: "workspace",
          command: "./mvnw test",
          cwd: "module-a",
          timeout_seconds: 900,
        },
      },
    },
    (action, input) => {
      calls.push({ action, input });
      return Promise.resolve({ commandId: "command" });
    },
  );
  assert.deepEqual(calls, [{
    action: "process_start",
    input: {
      workspaceId: "workspace",
      command: "./mvnw test",
      cwd: "module-a",
      timeoutSeconds: 900,
    },
  }]);
  assert.deepEqual(response, {
    jsonrpc: "2.0",
    id: 7,
    result: { content: [{ type: "text", text: '{\n  "commandId": "command"\n}' }] },
  });
});

Deno.test("returns remote failures as MCP tool errors", async () => {
  const response = await handleMcpRequest(
    {
      jsonrpc: "2.0",
      id: "failure",
      method: "tools/call",
      params: { name: "workspace_destroy", arguments: { workspace_id: "missing" } },
    },
    () => Promise.reject(new Error("Workspace not found")),
  );
  assert.deepEqual(response, {
    jsonrpc: "2.0",
    id: "failure",
    result: {
      content: [{ type: "text", text: "Workspace not found" }],
      isError: true,
    },
  });
});

Deno.test("maps every tool without forwarding credential fields", () => {
  assert.deepEqual(
    cloudInput("workspace_create", {
      repository: "owner/repository",
      commit_sha: "a".repeat(40),
      profile: "large",
      ttl_minutes: 240,
    }),
    {
      repository: "owner/repository",
      commitSha: "a".repeat(40),
      profile: "large",
      ttlMinutes: 240,
    },
  );
  assert.deepEqual(
    cloudInput("process_logs", {
      workspace_id: "workspace",
      session_id: "session",
      command_id: "command",
    }),
    {
      workspaceId: "workspace",
      processSessionId: "session",
      commandId: "command",
    },
  );
});
