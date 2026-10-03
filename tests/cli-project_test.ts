import assert from "node:assert/strict";
import { join } from "node:path";
import { projectContext, resolveScope } from "../cli/project.ts";

Deno.test("CLI resolves repository, class, and line scopes without leaving the project", async () => {
  const root = await Deno.makeTempDir();
  try {
    const source = "src/main/java/com/acme/OrderService.java";
    await Deno.mkdir(join(root, "src/main/java/com/acme"), { recursive: true });
    await Deno.writeTextFile(
      join(root, source),
      "package com.acme;\npublic class OrderService {}\n",
    );
    await new Deno.Command("git", { args: ["-C", root, "init", "-q"] }).output();
    const byClass = await resolveScope(root, {
      all: false,
      paths: [],
      classes: ["com.acme.OrderService"],
      selections: [],
    });
    assert.deepEqual(byClass.selectedPaths, [source]);
    const selected = await resolveScope(root, {
      all: false,
      paths: [],
      classes: [],
      selections: [`${source}:2-2`],
    });
    assert.deepEqual(selected.selections, [{ path: source, startLine: 2, endLine: 2 }]);
    const context = await projectContext(root, byClass);
    assert.deepEqual(context.modules[0].sourceRoots, [join(root, "src/main/java")]);
    assert.deepEqual(context.modules[0].testRoots, [join(root, "src/test/java")]);
    await assert.rejects(
      () => resolveScope(root, { all: false, paths: ["../"], classes: [], selections: [] }),
      /leaves the repository/,
    );
    await assert.rejects(
      () =>
        resolveScope(root, { all: false, paths: [], classes: [], selections: [`${source}:99`] }),
      /outside/,
    );
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
