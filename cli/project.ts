import { basename, join, relative, resolve, sep } from "node:path";

export type Scope = {
  selectedPaths: string[];
  selections: { path: string; startLine: number; endLine: number }[];
  projectWide: boolean;
};

export async function git(root: string, ...args: string[]): Promise<string> {
  const result = await new Deno.Command("git", {
    args: ["-C", root, ...args],
    stdout: "piped",
    stderr: "piped",
  }).output();
  if (!result.success) {
    throw new Error(new TextDecoder().decode(result.stderr).trim() || "Git command failed");
  }
  return new TextDecoder().decode(result.stdout).trim();
}

export async function repositoryRoot(path: string): Promise<string> {
  return await Deno.realPath(await git(resolve(path), "rev-parse", "--show-toplevel"));
}

export async function javaFiles(root: string): Promise<string[]> {
  const output = await git(
    root,
    "ls-files",
    "--cached",
    "--others",
    "--exclude-standard",
    "-z",
    "--",
    "*.java",
  );
  return output.split("\0").filter(Boolean).map((path) => path.replaceAll("\\", "/"));
}

async function checkedPath(root: string, raw: string, file = false): Promise<string> {
  if (!raw || raw.includes("\0") || raw === ".git" || raw.startsWith(".git/")) {
    throw new Error(`Invalid scope path: ${raw}`);
  }
  const canonicalRoot = await Deno.realPath(root);
  const absolute = resolve(canonicalRoot, raw);
  const real = await Deno.realPath(absolute);
  if (real !== canonicalRoot && !real.startsWith(canonicalRoot + sep)) {
    throw new Error(`Scope path leaves the repository: ${raw}`);
  }
  const stat = await Deno.stat(real);
  if (file ? !stat.isFile : !stat.isFile && !stat.isDirectory) {
    throw new Error(`Invalid scope path: ${raw}`);
  }
  const path = relative(canonicalRoot, real).replaceAll("\\", "/") || ".";
  if (
    path.split("/").some((part) => part === ".git" || part === ".jaipilot") ||
    (stat.isFile && !path.endsWith(".java"))
  ) {
    throw new Error(`Scope must be Java source or a source directory: ${raw}`);
  }
  return path;
}

export async function resolveScope(
  root: string,
  options: { all: boolean; paths: string[]; classes: string[]; selections: string[] },
): Promise<Scope> {
  const provided = Number(options.all) + Number(options.paths.length > 0) +
    Number(options.classes.length > 0) + Number(options.selections.length > 0);
  if (provided !== 1) {
    throw new Error("Choose exactly one scope: --all, --path, --class, or --selection");
  }
  if (options.all) return { selectedPaths: ["."], selections: [], projectWide: true };
  const paths = await Promise.all(options.paths.map((path) => checkedPath(root, path)));
  if (options.classes.length) {
    const files = await javaFiles(root);
    for (const name of options.classes) {
      if (!/^[\p{L}_$][\p{L}\p{N}_$]*(\.[\p{L}_$][\p{L}\p{N}_$]*)*$/u.test(name)) {
        throw new Error(`Invalid Java class: ${name}`);
      }
      const simple = name.split(".").at(-1)!;
      const candidates = files.filter((file) => basename(file) === `${simple}.java`);
      const matches: string[] = [];
      for (const file of candidates) {
        const path = await checkedPath(root, file, true);
        const source = await Deno.readTextFile(join(root, path));
        const pkg = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1] ?? "";
        if (name === simple || name === `${pkg}.${simple}`) matches.push(path);
      }
      if (matches.length !== 1) {
        throw new Error(
          matches.length ? `Class ${name} is ambiguous; use --path` : `Class not found: ${name}`,
        );
      }
      paths.push(await checkedPath(root, matches[0], true));
    }
  }
  const selections: Scope["selections"] = [];
  for (const value of options.selections) {
    const match = value.match(/^(.+):(\d+)(?:-(\d+))?$/);
    if (!match) throw new Error(`Invalid selection: ${value} (use path:start-end)`);
    const path = await checkedPath(root, match[1], true);
    if (!path.endsWith(".java")) throw new Error(`Selection must be a Java file: ${path}`);
    const startLine = Number(match[2]), endLine = Number(match[3] ?? match[2]);
    const lines = (await Deno.readTextFile(join(root, path))).split("\n").length;
    if (startLine < 1 || endLine < startLine || endLine > lines) {
      throw new Error(`Selection is outside ${path} (${lines} lines)`);
    }
    paths.push(path);
    selections.push({ path, startLine, endLine });
  }
  return { selectedPaths: [...new Set(paths)], selections, projectWide: false };
}

export async function projectContext(root: string, scope: Scope) {
  const files = await javaFiles(root);
  const moduleRoots = new Set<string>();
  for (const file of files) {
    const marker = "/src/main/java/";
    const index = (`/${file}`).indexOf(marker);
    if (index >= 0) moduleRoots.add(file.slice(0, index));
  }
  const modules = [...moduleRoots].sort().map((module) => ({
    name: module || basename(root),
    sourceRoots: [join(root, module, "src/main/java")],
    testRoots: [join(root, module, "src/test/java")],
    sdkHome: Deno.env.get("JAVA_HOME") ?? "",
  }));
  return {
    selectedPaths: scope.selectedPaths,
    projectDirectory: root,
    os: Deno.build.os,
    shell: Deno.build.os === "windows" ? "cmd.exe" : "/bin/sh",
    jdkHome: Deno.env.get("JAVA_HOME") ?? "",
    testRoots: modules.flatMap((module) => module.testRoots),
    modules,
  };
}
