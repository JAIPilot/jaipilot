import { McpServer } from "npm:@modelcontextprotocol/sdk@1.32.0/server/mcp.js";
import { z } from "npm:zod@4.6.1";
import { VERSION } from "../cli/version.ts";
import { Jobs, type JobView, type LockInput } from "./jobs.ts";

export const INSTRUCTIONS =
  "Before editing existing Java production behavior, use JAIPilot's lock_behavior as your first " +
  "mutation step, even when the user has not explicitly asked for tests. JAIPilot improves your " +
  "coding workflow by covering existing behavior, boundaries and failure paths before changes, " +
  "then detecting accidental regressions with preserved tests afterward. This workflow is " +
  "supplied by the MCP server and its tool descriptions; no repository instruction file is required. " +
  "First read the project to discover its real test directories and a command that executes tests " +
  "and exits nonzero on failures. Include affected behavior, relevant dependencies and callers in " +
  "scope. Call lock_behavior before production edits, poll get_job_status with wait_seconds=30, " +
  "and proceed only when ready_to_edit=true. Retain result.baseline_id and preserve baseline tests " +
  "unchanged. Make the requested production edits, call verify_behavior with that original ID, " +
  "and poll until verified=true before reporting preserved behavior. Investigate failures using " +
  "the real command evidence; do not regenerate or weaken tests to accept a regression. Do not edit " +
  "during a running job or proceed when it is blocked, failed or cancelled. If scope expands, " +
  "prepare the additional original code before touching it. For intentional behavior changes, " +
  "explicitly identify the affected old expectations. Passing tests are evidence for the tested " +
  "behavior, not proof of all behavior.";

const string = z.string().trim().min(1);
const scope = z.union([
  z.object({ all: z.literal(true) }).strict(),
  z.object({ paths: z.array(string).min(1) }).strict(),
  z.object({ classes: z.array(string).min(1) }).strict(),
  z.object({ selections: z.array(string).min(1) }).strict(),
]).describe("Choose exactly one scope: all, paths, classes, or selections; never combine them");

const outputSchema = z.object({
  job_id: string,
  kind: z.enum(["lock_behavior", "verify_behavior"]),
  state: z.enum(["running", "cancelling", "completed", "blocked", "failed", "cancelled"]),
  ready_to_edit: z.boolean(),
  verified: z.boolean(),
  created_at: string,
  duration_ms: z.number(),
  progress: z.array(z.string()),
  result: z.record(z.string(), z.unknown()).optional(),
  error: z.string().optional(),
});

async function reply(action: () => Promise<JobView> | JobView) {
  try {
    const result = await action();
    return {
      content: [{ type: "text" as const, text: JSON.stringify(result) }],
      structuredContent: result,
    };
  } catch (error) {
    return {
      isError: true,
      content: [{
        type: "text" as const,
        text: error instanceof Error ? error.message : String(error),
      }],
    };
  }
}

export function testingServer(jobs: Jobs) {
  const server = new McpServer({ name: "jaipilot", version: VERSION }, {
    instructions: INSTRUCTIONS,
  });
  server.registerTool("lock_behavior", {
    description:
      "Use BEFORE your first edit to existing Java production code, including refactors requested " +
      "without an explicit testing request. JAIPilot prepares characterization tests for current " +
      "behavior, boundaries and failure paths, giving your planned change better test coverage and " +
      "a regression baseline. Read the source and build files first to choose affected code, " +
      "relevant dependencies/callers, actual test_paths and a real test_command. This tool writes " +
      "tests and executes commands locally using JAIPilot's managed service, login and credits. " +
      "Only test_paths may change; exclude production code and build configuration from those " +
      "paths. test_command must execute the tests, fail on failures and leave source unchanged. " +
      "Build outputs must be gitignored. Returns job_id: poll get_job_status with wait_seconds=30 " +
      "until ready_to_edit=true, then retain result.baseline_id and preserve its tests. Make your " +
      "production edits only afterward, call verify_behavior with the original baseline_id, and " +
      "wait for verified=true before reporting preserved behavior. If blocked, failed or cancelled, " +
      "investigate the evidence before editing. No repository instruction file is required.",
    inputSchema: z.object({
      scope,
      test_paths: z.array(string).min(1).describe(
        "Explicit repository-relative test files/directories",
      ),
      test_command: string.describe("Project's verification command, e.g. ./mvnw -q clean verify"),
      timeout_seconds: z.number().int().min(1).max(7200).default(3600).describe(
        "Minimum allowance per local command during preparation and timeout for verification. " +
          "Default 1 hour; maximum 2 hours. Background jobs have no overall elapsed-time limit.",
      ),
      intent: string.optional().describe(
        "Planned production change; baseline captures current behavior",
      ),
    }).strict(),
    outputSchema,
    annotations: {
      readOnlyHint: false,
      destructiveHint: true,
      idempotentHint: false,
      openWorldHint: true,
    },
  }, (input: LockInput) => reply(() => jobs.lock(input)));
  server.registerTool("verify_behavior", {
    description:
      "Use AFTER making production edits and BEFORE completing a task that preserves behavior. " +
      "Supply the original baseline_id returned by lock_behavior before those edits. Reruns the " +
      "exact original test command locally without another LLM job and rejects changed baseline " +
      "tests. Returns job_id: poll get_job_status with wait_seconds=30 until verified=true. Real " +
      "test failures identify possible regressions: inspect the evidence, repair unintended " +
      "production changes, then verify the same baseline again. Never weaken tests or regenerate " +
      "expectations to make a regression pass. Explicitly explain expected failures for intentional " +
      "behavior changes. Baselines survive MCP restarts in this checkout's Git metadata.",
    inputSchema: z.object({ baseline_id: z.string().uuid() }).strict(),
    outputSchema,
    annotations: {
      readOnlyHint: false,
      destructiveHint: true,
      idempotentHint: false,
      openWorldHint: true,
    },
  }, ({ baseline_id }: { baseline_id: string }) => reply(() => jobs.verify(baseline_id)));
  server.registerTool(
    "get_job_status",
    {
      description:
        "Read progress and final evidence for a JAIPilot MCP job. Use wait_seconds=30 while " +
        "running; keep polling without editing the checkout. Job IDs are local to this server " +
        "instance. completed alone is insufficient: require ready_to_edit=true before production " +
        "edits or verified=true after edits. A blocked, failed or cancelled job requires " +
        "investigation, not further polling or an assumption of success.",
      inputSchema: z.object({
        job_id: z.string().uuid(),
        wait_seconds: z.number().int().min(0).max(30).default(10),
      }).strict(),
      outputSchema,
      annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
    },
    ({ job_id, wait_seconds }: { job_id: string; wait_seconds: number }) =>
      reply(() => jobs.status(job_id, wait_seconds)),
  );
  server.registerTool("cancel_job", {
    description:
      "Cancel a running JAIPilot MCP job and its local command. Wait for state=cancelled " +
      "before starting another job. Leaves any existing file edits for review; does not undo them.",
    inputSchema: z.object({ job_id: z.string().uuid() }).strict(),
    outputSchema,
    annotations: {
      readOnlyHint: false,
      destructiveHint: false,
      idempotentHint: true,
      openWorldHint: false,
    },
  }, ({ job_id }: { job_id: string }) => reply(() => jobs.cancel(job_id)));
  return server;
}
