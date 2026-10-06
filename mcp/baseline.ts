import { createHash } from "node:crypto";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { git, type Scope } from "../cli/project.ts";
import type { runCommand } from "../cli/agent.ts";

export type Files = Record<string, string>;
export type Baseline = {
  formatVersion: 1;
  id: string;
  repository: string;
  createdAt: string;
  scope: Scope;
  intent: string;
  testPaths: string[];
  testCommand: string;
  timeoutSeconds: number;
  protectedFiles: Files;
  testFiles: Files;
  verification: Awaited<ReturnType<typeof runCommand>>;
};

export async function stateDirectory(root: string): Promise<string> {
  // --git-path gives each worktree its own state, outside the files being characterized.
  return resolve(root, await git(root, "rev-parse", "--git-path", "jaipilot-mcp"));
}

export function under(path: string, roots: string[]): boolean {
  return roots.some((root) => path === root || path.startsWith(`${root}/`));
}

export async function testPaths(root: string, paths: string[]): Promise<string[]> {
  root = await Deno.realPath(root);
  const checked: string[] = [];
  for (const raw of paths) {
    if (!raw || raw.includes("\0") || isAbsolute(raw)) {
      throw new Error("test_paths must be repository-relative test files or directories");
    }
    const absolute = resolve(root, raw);
    const path = relative(root, absolute).replaceAll("\\", "/");
    if (!path || path === ".." || path.startsWith("../") || path.split("/").includes(".git")) {
      throw new Error(`Invalid test path: ${raw}`);
    }
    // Test directories may not exist yet. Check their existing ancestors without following links.
    let current = absolute;
    while (current !== root) {
      try {
        const info = await Deno.lstat(current);
        if (info.isSymlink) throw new Error(`Test path contains a symbolic link: ${raw}`);
      } catch (error) {
        if (!(error instanceof Deno.errors.NotFound)) throw error;
      }
      current = dirname(current);
    }
    const realRoot = await Deno.realPath(root);
    if (absolute !== realRoot && !absolute.startsWith(realRoot + sep)) {
      throw new Error(`Test path leaves the repository: ${raw}`);
    }
    checked.push(path);
  }
  return [...new Set(checked)];
}

export async function snapshot(root: string): Promise<Files> {
  const output = await new Deno.Command(Deno.build.os === "windows" ? "git.exe" : "git", {
    args: ["-C", root, "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
    stdout: "piped",
    stderr: "piped",
  }).output();
  if (!output.success) throw new Error(new TextDecoder().decode(output.stderr));
  const paths = [...new Set(new TextDecoder().decode(output.stdout).split("\0").filter(Boolean))];
  const files: Files = {};
  for (const path of paths.sort()) {
    const absolute = join(root, path);
    let info: Deno.FileInfo;
    try {
      info = await Deno.lstat(absolute);
    } catch (error) {
      if (error instanceof Deno.errors.NotFound) continue; // Includes pre-existing deletions.
      throw error;
    }
    const hash = createHash("sha256");
    if (info.isSymlink) {
      hash.update(`symlink:${await Deno.readLink(absolute)}`);
    } else if (info.isFile) {
      const file = await Deno.open(absolute, { read: true });
      await file.readable.pipeTo(
        new WritableStream({
          write: (chunk) => {
            hash.update(chunk);
          },
        }),
      );
      hash.update(`:${(info.mode ?? 0) & 0o111}`);
    } else {
      throw new Error(`Unsupported repository entry: ${path}`);
    }
    files[path.replaceAll("\\", "/")] = hash.digest("hex");
  }
  return files;
}

export function select(files: Files, paths: string[], include: boolean): Files {
  return Object.fromEntries(
    Object.entries(files).filter(([path]) => under(path, paths) === include),
  );
}

export function changed(before: Files, after: Files): string[] {
  return [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter((path) => before[path] !== after[path]).sort();
}

export async function saveBaseline(root: string, baseline: Baseline): Promise<void> {
  const directory = join(await stateDirectory(root), "baselines");
  await Deno.mkdir(directory, { recursive: true, mode: 0o700 });
  const temporary = join(directory, `${baseline.id}.tmp`);
  try {
    await Deno.writeTextFile(temporary, JSON.stringify(baseline), { mode: 0o600 });
    await Deno.rename(temporary, join(directory, `${baseline.id}.json`));
  } finally {
    await Deno.remove(temporary).catch(() => {});
  }
}

export async function loadBaseline(root: string, id: string): Promise<Baseline> {
  if (!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/.test(id)) {
    throw new Error("Invalid baseline_id");
  }
  let baseline: Baseline;
  try {
    baseline = JSON.parse(
      await Deno.readTextFile(join(await stateDirectory(root), "baselines", `${id}.json`)),
    );
  } catch (error) {
    if (error instanceof Deno.errors.NotFound) throw new Error(`Baseline not found: ${id}`);
    throw error;
  }
  if (
    baseline.formatVersion !== 1 || baseline.id !== id || baseline.repository !== root ||
    typeof baseline.testCommand !== "string" || !baseline.testCommand.trim() ||
    !Number.isInteger(baseline.timeoutSeconds) || baseline.timeoutSeconds < 1 ||
    baseline.timeoutSeconds > 7200 || !Array.isArray(baseline.testPaths) ||
    !baseline.testPaths.length || !baseline.testFiles || !baseline.protectedFiles
  ) throw new Error("Invalid baseline manifest");
  return baseline;
}
