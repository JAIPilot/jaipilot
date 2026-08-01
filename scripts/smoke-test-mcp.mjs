#!/usr/bin/env node

import { spawn } from "node:child_process";

const [command, ...args] = process.argv.slice(2);
if (!command) {
  process.stderr.write("Usage: scripts/smoke-test-mcp.mjs <command> [args...]\n");
  process.exit(2);
}

const child = spawn(command, args, { stdio: ["pipe", "pipe", "pipe"] });
let pendingStdout = "";
let stderr = "";
let completed = false;

const timeout = setTimeout(() => finish(new Error(`MCP smoke test timed out. stderr:\n${stderr}`)), 10_000);
child.stdout.setEncoding("utf8");
child.stderr.setEncoding("utf8");
child.stderr.on("data", chunk => {
  stderr += chunk;
});
child.stdout.on("data", chunk => {
  pendingStdout += chunk;
  const lines = pendingStdout.split("\n");
  pendingStdout = lines.pop() ?? "";
  for (const line of lines.filter(Boolean)) {
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      finish(new Error(`MCP stdout contained non-JSON data: ${line}`));
      return;
    }
    if (message.id === 1 && message.result) {
      child.stdin.write('{"jsonrpc":"2.0","method":"notifications/initialized"}\n');
      child.stdin.write('{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}\n');
    }
    if (message.id === 2 && Array.isArray(message.result?.tools)) {
      const names = new Set(message.result.tools.map(tool => tool.name));
      if (!names.has("jaipilot_prepare_tests") || !names.has("jaipilot_prepare_cleanup")) {
        finish(new Error(`MCP tool list is incomplete: ${[...names].join(", ")}`));
        return;
      }
      finish();
      return;
    }
  }
});
child.once("error", finish);
child.once("exit", (status, signal) => {
  if (!completed) {
    finish(new Error(`MCP server exited early (${signal ?? status}). stderr:\n${stderr}`));
  }
});

child.stdin.write('{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1"}}}\n');

function finish(error) {
  if (completed) return;
  completed = true;
  clearTimeout(timeout);
  child.kill("SIGTERM");
  if (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  } else {
    process.stdout.write("MCP initialize and tool discovery passed\n");
  }
}
