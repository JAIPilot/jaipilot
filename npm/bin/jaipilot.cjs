#!/usr/bin/env node
const { spawn } = require("node:child_process");
const { join } = require("node:path");
const child = spawn(
  join(__dirname, `jaipilot-native${process.platform === "win32" ? ".exe" : ""}`),
  process.argv.slice(2),
  { stdio: "inherit" },
);
child.on("error", (error) => {
  console.error(`JAIPilot: ${error.message}. Reinstall with npm install -g @jaipilot/cli.`);
  process.exitCode = 1;
});
// The native CLI handles cancellation; keep the launcher alive until it finishes cleanup.
process.on("SIGINT", () => {});
process.on("SIGTERM", () => {
  child.kill("SIGTERM");
});
child.on("exit", (code, signal) => {
  process.exitCode = code ?? (signal === "SIGINT" ? 130 : 1);
});
