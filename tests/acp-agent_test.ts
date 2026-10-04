import assert from "node:assert/strict";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import * as acp from "npm:@agentclientprotocol/sdk@1.7.0";
import { OUTCOMES, promptScope, scopeArguments, TestingAgent, testingApp } from "../acp/agent.ts";
import { runWorkflow, type WorkflowResult } from "../cli/agent.ts";

async function fixture() {
  const root = await Deno.makeTempDir();
  const path = "module/src/main/java/com/acme/OrderService.java";
  await Deno.mkdir(join(root, "module/src/main/java/com/acme"), { recursive: true });
  await Deno.writeTextFile(join(root, path), "package com.acme;\npublic class OrderService {}\n");
  await new Deno.Command("git", { args: ["-C", root, "init", "-q"] }).output();
  return { root, path };
}

function connect(run: typeof runWorkflow, options: {
  token?: () => Promise<string>;
  login?: () => Promise<string>;
  permission?: (params: acp.RequestPermissionRequest) => Promise<acp.RequestPermissionResponse>;
  terminalExit?: () => Promise<acp.WaitForTerminalExitResponse>;
} = {}) {
  const toAgent = new TransformStream<acp.AnyMessage, acp.AnyMessage>();
  const toClient = new TransformStream<acp.AnyMessage, acp.AnyMessage>();
  const updates: acp.SessionNotification[] = [];
  const calls: { method: string; params?: unknown }[] = [];
  const agent = new TestingAgent({
    token: options.token ?? (() => Promise.resolve("test-token")),
    login: options.login ?? (() => Promise.resolve("test@example.com")),
    run,
  });
  const server = testingApp(agent).connect({
    readable: toAgent.readable,
    writable: toClient.writable,
  });
  const client = acp.client({ name: "test-ide" })
    .onNotification("session/update", ({ params }) => {
      updates.push(params);
    })
    .onRequest("session/request_permission", ({ params }) => {
      calls.push({ method: "permission", params });
      return options.permission?.(params) ??
        { outcome: { outcome: "selected", optionId: "allow" } };
    })
    .onRequest("terminal/create", ({ params }) => {
      calls.push({ method: "create", params });
      return { terminalId: "test-terminal" };
    })
    .onRequest("terminal/wait_for_exit", () => options.terminalExit?.() ?? { exitCode: 0 })
    .onRequest("terminal/output", () => ({ output: "tests passed", truncated: false }))
    .onRequest("terminal/kill", () => {
      calls.push({ method: "kill" });
      return {};
    })
    .onRequest("terminal/release", () => {
      calls.push({ method: "release" });
      return {};
    })
    .connect({ readable: toClient.readable, writable: toAgent.writable });
  return {
    agent,
    client: client.agent,
    updates,
    calls,
    close: async () => {
      agent.stop();
      client.close();
      server.close();
      await Promise.all([client.closed, server.closed]);
    },
  };
}

async function initialize(client: acp.ClientContext) {
  return await client.request("initialize", {
    protocolVersion: 1,
    clientCapabilities: { terminal: true },
  });
}

function result(workflow: string, scope: WorkflowResult["scope"]): WorkflowResult {
  return {
    workflow,
    scope,
    status: "complete",
    summary: "Verified tests.",
    verification: { tests: "tests passed", coverage: "not configured" },
    testFailures: [],
    nextActions: [],
    gitStatus: " M module/src/test/java/OrderServiceTest.java",
    durationMs: 100,
  };
}

Deno.test("ACP exposes six outcomes, real authentication, and config selectors", async () => {
  const { root } = await fixture();
  let signedIn = false;
  const h = connect(() => Promise.reject(new Error("must not run")), {
    token: () => signedIn ? Promise.resolve("test") : Promise.reject(new Error("Sign in first")),
    login: () => {
      signedIn = true;
      return Promise.resolve("test@example.com");
    },
  });
  try {
    const init = await initialize(h.client);
    assert.equal(init.agentInfo?.name, "jaipilot");
    assert.equal(init.authMethods?.[0].id, "jaipilot-login");
    assert.equal(init.agentCapabilities?.loadSession, false);
    await assert.rejects(
      () => h.client.request("session/new", { cwd: root, mcpServers: [] }),
      (error: unknown) => error instanceof acp.RequestError && error.code === -32000,
    );
    await h.client.request("authenticate", { methodId: "jaipilot-login" });
    const session = await h.client.request("session/new", { cwd: root, mcpServers: [] });
    assert.equal(session.modes?.availableModes.length, 6);
    assert.equal(session.configOptions?.[0].currentValue, "generate_tests");
    const changed = await h.client.request("session/set_config_option", {
      sessionId: session.sessionId,
      configId: "outcome",
      value: "fix_tests",
    });
    assert.equal(changed.configOptions[0].currentValue, "fix_tests");
    const commands = h.updates.find((n) => n.update.sessionUpdate === "available_commands_update");
    assert.ok(commands);
    await assert.rejects(
      () =>
        h.client.request("session/new", {
          cwd: root,
          mcpServers: [{ name: "untrusted", command: "/invalid", args: [], env: [] }],
        }),
      /does not use custom MCP/,
    );
    await assert.rejects(
      () =>
        h.client.request("session/set_mode", { sessionId: session.sessionId, modeId: "unknown" }),
      /Unknown testing outcome/,
    );
  } finally {
    await h.close();
    await Deno.remove(root, { recursive: true });
  }
});

Deno.test("ACP scopes respect module cwd, quoted paths, IDE links, and repository bounds", async () => {
  const { root, path } = await fixture();
  try {
    assert.deepEqual(scopeArguments('--path "folder with spaces" --class com.acme.OrderService'), [
      "--path",
      "folder with spaces",
      "--class",
      "com.acme.OrderService",
    ]);
    assert.deepEqual(
      (await promptScope(
        root,
        "--path src/main/java/com/acme/OrderService.java",
        [],
        join(root, "module"),
      )).selectedPaths,
      [path],
    );
    assert.deepEqual(
      (await promptScope(root, "", [{
        type: "resource_link",
        uri: pathToFileURL(join(root, path)).href,
        name: "OrderService",
      }])).selectedPaths,
      [path],
    );
    assert.deepEqual(
      (await promptScope(
        root,
        "--selection src/main/java/com/acme/OrderService.java:2-2",
        [],
        join(root, "module"),
      )).selections,
      [{ path, startLine: 2, endLine: 2 }],
    );
    await assert.rejects(
      () => promptScope(root, "please generate tests", []),
      /Choose exactly one scope/,
    );
    await assert.rejects(() => promptScope(root, "--path ..", []), /leaves the repository/);
    await assert.rejects(
      () => promptScope(root, "--all --class OrderService", []),
      /exactly one scope/,
    );
    await assert.rejects(
      () => promptScope(root, "--selection OrderService.java", []),
      /path:start-end/,
    );
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});

Deno.test("ACP routes every outcome through approved client terminals and reports evidence", async () => {
  const { root } = await fixture();
  let selected: string | undefined;
  const h = connect(async (cwd, workflow, scope, signal, options) => {
    selected = workflow;
    assert.equal(options?.protocolVersion, 4);
    assert.ok(options?.userRequest);
    const command = await options!.execute!(cwd, {
      command: "echo verified",
      purpose: "test",
      timeoutSeconds: 5,
    }, signal);
    assert.equal(command.exitCode, 0);
    assert.equal(command.output, "tests passed");
    return result(workflow, scope);
  });
  try {
    await initialize(h.client);
    const session = await h.client.request("session/new", { cwd: root, mcpServers: [] });
    for (const outcome of OUTCOMES) {
      assert.equal(
        (await h.client.request("session/prompt", {
          sessionId: session.sessionId,
          prompt: [{ type: "text", text: `/${outcome.id} --class com.acme.OrderService` }],
        })).stopReason,
        "end_turn",
      );
      assert.equal(selected, outcome.id);
    }
    assert.equal(h.calls.filter((c) => c.method === "permission").length, 12);
    assert.equal(h.calls.filter((c) => c.method === "create").length, 6);
    assert.equal(h.calls.filter((c) => c.method === "release").length, 6);
    assert.ok(
      h.updates.some((n) =>
        n.update.sessionUpdate === "agent_message_chunk" && n.update.content.type === "text" &&
        n.update.content.text.includes("coverage: not configured")
      ),
    );
    assert.ok(
      h.updates.some((n) =>
        n.update.sessionUpdate === "tool_call_update" && n.update.status === "completed"
      ),
    );
  } finally {
    await h.close();
    await Deno.remove(root, { recursive: true });
  }
});

Deno.test("ACP rejected commands never execute and cancel ends a pending approval", async () => {
  const { root } = await fixture();
  let permissionCalls = 0;
  const h = connect(async (cwd, workflow, scope, signal, options) => {
    await options!.execute!(cwd, { command: "echo forbidden", timeoutSeconds: 5 }, signal);
    return result(workflow, scope);
  }, {
    permission: () =>
      Promise.resolve({
        outcome: { outcome: "selected", optionId: ++permissionCalls === 1 ? "allow" : "reject" },
      }),
  });
  try {
    await initialize(h.client);
    const session = await h.client.request("session/new", { cwd: root, mcpServers: [] });
    const response = await h.client.request("session/prompt", {
      sessionId: session.sessionId,
      prompt: [{ type: "text", text: "/generate_tests --all" }],
    });
    assert.equal(response.stopReason, "cancelled");
    assert.equal(h.calls.filter((c) => c.method === "create").length, 0);
  } finally {
    await h.close();
    await Deno.remove(root, { recursive: true });
  }

  let entered!: () => void;
  const pending = new Promise<void>((resolve) => entered = resolve);
  const wait = connect(() => Promise.reject(new Error("must not run")), {
    permission: () => {
      entered();
      return new Promise(() => {});
    },
  });
  const second = await fixture();
  try {
    await initialize(wait.client);
    const session = await wait.client.request("session/new", { cwd: second.root, mcpServers: [] });
    const turn = wait.client.request("session/prompt", {
      sessionId: session.sessionId,
      prompt: [{ type: "text", text: "/generate_tests --all" }],
    });
    await pending;
    await wait.client.notify("session/cancel", { sessionId: session.sessionId });
    assert.equal((await turn).stopReason, "cancelled");
  } finally {
    await wait.close();
    await Deno.remove(second.root, { recursive: true });
  }
});

Deno.test("ACP cancellation and timeout kill and release active terminals", async () => {
  for (const cancel of [true, false]) {
    const { root } = await fixture();
    let entered!: () => void;
    const pending = new Promise<void>((resolve) => entered = resolve);
    const h = connect(async (cwd, workflow, scope, signal, options) => {
      const output = await options!.execute!(
        cwd,
        { command: "wait for tests", timeoutSeconds: 1 },
        signal,
      );
      assert.equal(output.exitCode, 124);
      assert.equal(output.timedOut, true);
      return result(workflow, scope);
    }, {
      terminalExit: () => {
        entered();
        return new Promise(() => {});
      },
    });
    try {
      await initialize(h.client);
      const session = await h.client.request("session/new", { cwd: root, mcpServers: [] });
      const turn = h.client.request("session/prompt", {
        sessionId: session.sessionId,
        prompt: [{ type: "text", text: "/fix_tests --all" }],
      });
      await pending;
      if (cancel) await h.client.notify("session/cancel", { sessionId: session.sessionId });
      assert.equal((await turn).stopReason, cancel ? "cancelled" : "end_turn");
      assert.equal(h.calls.filter((c) => c.method === "kill").length, 1);
      assert.equal(h.calls.filter((c) => c.method === "release").length, 1);
    } finally {
      await h.close();
      await Deno.remove(root, { recursive: true });
    }
  }
});

Deno.test("ACP refuses concurrent mutations of the same repository", async () => {
  const { root } = await fixture();
  let entered!: () => void;
  const pending = new Promise<void>((resolve) => entered = resolve);
  const h = connect(() => Promise.reject(new Error("must not run")), {
    permission: () => {
      entered();
      return new Promise(() => {});
    },
  });
  try {
    await initialize(h.client);
    const a = await h.client.request("session/new", { cwd: root, mcpServers: [] });
    const b = await h.client.request("session/new", { cwd: root, mcpServers: [] });
    const turn = h.client.request("session/prompt", {
      sessionId: a.sessionId,
      prompt: [{ type: "text", text: "/fix_tests --all" }],
    });
    await pending;
    await assert.rejects(
      () =>
        h.client.request("session/prompt", {
          sessionId: b.sessionId,
          prompt: [{ type: "text", text: "/fix_tests --all" }],
        }),
      /already running/,
    );
    await h.client.notify("session/cancel", { sessionId: a.sessionId });
    await turn;
  } finally {
    await h.close();
    await Deno.remove(root, { recursive: true });
  }
});
