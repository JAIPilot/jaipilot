#!/usr/bin/env -S deno run -A
import { ndJsonStream } from "npm:@agentclientprotocol/sdk@1.7.0";
import { TestingAgent, testingApp } from "./agent.ts";
import { login } from "../cli/auth.ts";
import { VERSION } from "../cli/version.ts";

export async function main(args: string[]) {
  if (args.length === 1 && args[0] === "--version") {
    console.log(`JAIPilot ACP ${VERSION}`);
    return;
  }
  if (args.length === 1 && args[0] === "--login") {
    console.error(`Signed in as ${await login()}`);
    return;
  }
  if (args.length && !(args.length === 1 && args[0] === "acp")) {
    throw new Error("Use jaipilot-acp [acp | --login | --version]");
  }
  const agent = new TestingAgent();
  const connection = testingApp(agent).connect(
    ndJsonStream(Deno.stdout.writable, Deno.stdin.readable),
  );
  connection.signal.addEventListener("abort", () => agent.stop(), { once: true });
  const stop = async () => {
    await agent.stop();
    connection.close();
    Deno.stdin.close();
  };
  Deno.addSignalListener("SIGINT", stop);
  if (Deno.build.os !== "windows") Deno.addSignalListener("SIGTERM", stop);
  try {
    await connection.closed;
  } finally {
    await agent.stop();
    Deno.removeSignalListener("SIGINT", stop);
    if (Deno.build.os !== "windows") Deno.removeSignalListener("SIGTERM", stop);
  }
}

if (import.meta.main) {
  try {
    await main(Deno.args);
    Deno.exit(0);
  } catch (error) {
    console.error(`JAIPilot ACP: ${error instanceof Error ? error.message : String(error)}`);
    Deno.exit(1);
  }
}
