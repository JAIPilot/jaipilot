#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import process from "node:process";

const root = resolve(import.meta.dirname, "..");
const packageMetadata = json("package.json");
const pluginFiles = [
  "plugin/jaipilot/plugin.json",
  "plugin/jaipilot/.codex-plugin/plugin.json",
  "plugin/jaipilot/.claude-plugin/plugin.json"
];
const skills = ["jaipilot-generate-tests", "jaipilot-clean-java"];

for (const file of pluginFiles) {
  const manifest = json(file);
  assert(manifest.name === "jaipilot", `${file}: name must be jaipilot`);
  assert(manifest.version === packageMetadata.version,
    `${file}: version must match package.json (${packageMetadata.version})`);
  assert(typeof manifest.description === "string" && manifest.description.length >= 20,
    `${file}: description is missing or too short`);
}

const mcp = json("plugin/jaipilot/.mcp.json");
assert(mcp.mcpServers?.jaipilot?.command === "jaipilot-mcp",
  "plugin/jaipilot/.mcp.json: jaipilot must launch jaipilot-mcp");
assert(Array.isArray(mcp.mcpServers.jaipilot.args),
  "plugin/jaipilot/.mcp.json: args must be an array");

for (const skillName of skills) {
  const directory = `plugin/jaipilot/skills/${skillName}`;
  const skill = text(`${directory}/SKILL.md`);
  const frontmatter = skill.match(/^---\n([\s\S]*?)\n---\n/);
  assert(frontmatter !== null, `${directory}/SKILL.md: YAML frontmatter is required`);
  assert(new RegExp(`^name:\\s*${escapeRegExp(skillName)}\\s*$`, "m").test(frontmatter[1]),
    `${directory}/SKILL.md: name must match its directory`);
  assert(/^description:\s*\S.+$/m.test(frontmatter[1]),
    `${directory}/SKILL.md: description is required`);
  assert(!/\b(?:TODO|TBD)\b/i.test(skill), `${directory}/SKILL.md: unresolved placeholder found`);

  const openai = text(`${directory}/agents/openai.yaml`);
  for (const field of ["display_name", "short_description", "default_prompt"]) {
    assert(new RegExp(`^\\s*${field}:\\s*.+$`, "m").test(openai),
      `${directory}/agents/openai.yaml: ${field} is required`);
  }
}

console.log(`Validated JAIPilot plugin ${packageMetadata.version} and ${skills.length} Agent Skills.`);

function text(relativePath) {
  return readFileSync(resolve(root, relativePath), "utf8");
}

function json(relativePath) {
  return JSON.parse(text(relativePath));
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
