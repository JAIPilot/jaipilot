#!/usr/bin/env -S deno run -A
import { login, logout, status } from "./auth.ts";
import { runWorkflow, workflows } from "./agent.ts";
import { repositoryRoot, resolveScope } from "./project.ts";
import { VERSION } from "./version.ts";
import { shouldAutoUpdate, updateCli } from "./update.ts";

const HELP = `JAIPilot CLI — high-quality Java tests with measured coverage

Usage:
  jaipilot auth login|status|logout
  jaipilot workflows
  jaipilot update [--check]
  jaipilot run <workflow> [--repo DIR] (--all | --path PATH... | --class CLASS... | --selection FILE:START-END...) [--json]

Examples:
  jaipilot run improve_coverage --all
  jaipilot run stabilize_flaky_tests --path src/test/java/com/acme/OrderTest.java
  jaipilot run generate_tests --class com.acme.OrderService --class com.acme.InvoiceService
  jaipilot run improve_coverage --selection src/main/java/com/acme/OrderService.java:42-88

Commands run locally in the selected repository. JAIPilot may send selected source and test context
to its managed agent for parallel class work. Review local changes with git diff afterward.`;

type RunOptions = {
  repo: string;
  all: boolean;
  paths: string[];
  classes: string[];
  selections: string[];
  json: boolean;
};

function parseRun(args: string[]): RunOptions {
  const options: RunOptions = {
    repo: Deno.cwd(),
    all: false,
    paths: [],
    classes: [],
    selections: [],
    json: false,
  };
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--all") options.all = true;
    else if (arg === "--json") options.json = true;
    else if (["--repo", "--path", "--class", "--selection"].includes(arg)) {
      const value = args[++i];
      if (!value || value.startsWith("--")) throw new Error(`Missing value for ${arg}`);
      if (arg === "--repo") options.repo = value;
      if (arg === "--path") options.paths.push(value);
      if (arg === "--class") options.classes.push(value);
      if (arg === "--selection") options.selections.push(value);
    } else throw new Error(`Unknown option: ${arg}`);
  }
  return options;
}

export async function main(args: string[]): Promise<number> {
  const [command, subcommand, ...rest] = args;
  if (command === "--version" || command === "version") {
    console.log(`JAIPilot CLI ${VERSION}`);
    return 0;
  }
  if (!command || ["help", "--help", "-h"].includes(command)) {
    console.log(HELP);
    return 0;
  }
  if (command === "auth") {
    if (rest.length) throw new Error("Unexpected auth arguments");
    if (subcommand === "login") console.log(`Signed in as ${await login()}`);
    else if (subcommand === "status") {
      const email = await status();
      console.log(email ? `Signed in as ${email}` : "Not signed in");
    } else if (subcommand === "logout") {
      await logout();
      console.log("Signed out");
    } else throw new Error("Use `jaipilot auth login|status|logout`");
    return 0;
  }
  if (command === "update") {
    if (rest.length || (subcommand && subcommand !== "--check")) {
      throw new Error("Use `jaipilot update [--check]`");
    }
    await updateCli([], { checkOnly: subcommand === "--check" });
    return 0;
  }
  if (command === "workflows") {
    if (subcommand) throw new Error("Unexpected workflows arguments");
    for (const item of await workflows()) console.log(`${item.id.padEnd(24)} ${item.description}`);
    return 0;
  }
  if (command === "run") {
    if (!subcommand || subcommand.startsWith("--")) {
      throw new Error("Specify a workflow; run `jaipilot workflows`");
    }
    const options = parseRun(rest);
    const root = await repositoryRoot(options.repo);
    const scope = await resolveScope(root, options);
    const controller = new AbortController();
    const cancel = () => controller.abort();
    Deno.addSignalListener("SIGINT", cancel);
    try {
      const result = await runWorkflow(root, subcommand, scope, controller.signal);
      if (options.json) console.log(JSON.stringify(result, null, 2));
      else {
        console.log(`${result.status === "complete" ? "✓" : "!"} ${result.summary}`);
        const verification = result.verification as Record<string, unknown> | undefined;
        if (verification) {
          for (const key of ["build", "tests", "coverage"]) {
            if (verification[key]) console.log(`${key}: ${verification[key]}`);
          }
          for (const key of ["lineCoverageBefore", "lineCoverageAfter"]) {
            if (verification[key] != null) console.log(`${key}: ${verification[key]}`);
          }
        }
        if (result.gitStatus) console.log(`\nGit status:\n${result.gitStatus}`);
      }
      return result.status === "complete" ? 0 : 2;
    } finally {
      Deno.removeSignalListener("SIGINT", cancel);
    }
  }
  throw new Error(`Unknown command: ${command}`);
}

if (import.meta.main) {
  try {
    const updated = shouldAutoUpdate(Deno.args)
      ? await updateCli(Deno.args, { automatic: true })
      : null;
    Deno.exitCode = updated ?? await main(Deno.args);
  } catch (error) {
    console.error(`JAIPilot: ${error instanceof Error ? error.message : String(error)}`);
    Deno.exitCode = 1;
  }
}
