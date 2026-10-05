import { McpServer } from "npm:@modelcontextprotocol/sdk@1.32.0/server/mcp.js";
import { z } from "npm:zod@4.6.1";
import { VERSION } from "../cli/version.ts";
import { Jobs, type JobView, type LockInput } from "./jobs.ts";

export const INSTRUCTIONS =
  "Before editing existing Java production behavior, call lock_behavior for the planned scope. " +
  "Discover the project's real test command and test directories first. Poll get_job_status until " +
  "ready_to_edit is true; do not edit during a job or when it is blocked, failed or cancelled. " +
  "Preserve the returned baseline tests. After edits call verify_behavior with baseline_id and " +
  "wait until verified is true. Keep the original baseline; do not regenerate expectations after " +
  "a failure. Passing tests are evidence for the tested behavior, not proof of all behavior.";

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
      "Start characterization tests BEFORE editing Java production code in this repository. " +
      "Writes tests and runs local commands through JAIPilot's managed service using existing login " +
      "and credits. Only test_paths may change. test_command must run the actual tests, fail on test " +
      "failures and leave source files unchanged. Build outputs must be gitignored. Returns a job; " +
      "poll get_job_status until ready_to_edit=true and retain result.baseline_id.",
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
      "Start verification AFTER production edits against the original baseline. Reuses its " +
      "exact test command and rejects changed baseline tests. Runs locally without another LLM job. " +
      "Poll get_job_status until verified=true or investigate the reported failure; do not regenerate " +
      "tests to accept a regression. Baselines survive MCP restarts in this checkout's Git metadata.",
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
        "running. Job IDs are local to this server instance. completed alone is insufficient: check " +
        "ready_to_edit for preparation or verified for verification.",
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
