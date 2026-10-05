import process from "node:process";
import { StdioServerTransport } from "npm:@modelcontextprotocol/sdk@1.32.0/server/stdio.js";
import { repositoryRoot } from "../cli/project.ts";
import { Jobs } from "./jobs.ts";
import { testingServer } from "./server.ts";

export async function serve(args: string[]) {
  if (args.length === 1 && ["--help", "-h"].includes(args[0])) {
    console.log(
      "Usage: jaipilot mcp [--repo DIR]\nLocal stdio MCP server for Java behavior baselines.",
    );
    return;
  }
  if (args.length && !(args.length === 2 && args[0] === "--repo" && args[1])) {
    throw new Error("Use `jaipilot mcp [--repo DIR]`");
  }
  const jobs = new Jobs(await repositoryRoot(args[1] ?? Deno.cwd()));
  const server = testingServer(jobs);
  let done!: () => void;
  const closed = new Promise<void>((resolve) => {
    done = resolve;
  });
  let stopping: Promise<void> | undefined;
  const stop = () =>
    stopping ??= (async () => {
      await jobs.stop();
      await server.close();
      done();
    })();
  server.server.onclose = () => {
    void stop();
  };
  server.server.onerror = (error: Error) => console.error(`JAIPilot MCP: ${error.message}`);
  const end = () => {
    void stop();
  };
  process.stdin.once("end", end);
  Deno.addSignalListener("SIGINT", end);
  if (Deno.build.os !== "windows") Deno.addSignalListener("SIGTERM", end);
  try {
    await server.connect(new StdioServerTransport());
    await closed;
  } finally {
    await stop();
    process.stdin.off("end", end);
    Deno.removeSignalListener("SIGINT", end);
    if (Deno.build.os !== "windows") Deno.removeSignalListener("SIGTERM", end);
  }
}
