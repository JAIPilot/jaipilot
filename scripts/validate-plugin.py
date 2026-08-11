#!/usr/bin/env python3
"""Validate JAIPilot's dependency-free, skills-only plugin."""

from __future__ import annotations

import json
import re
import stat
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
PLUGIN = ROOT / "plugins" / "jaipilot"
PLUGIN_FILES = (
    PLUGIN / "plugin.json",
    PLUGIN / ".codex-plugin" / "plugin.json",
    PLUGIN / ".claude-plugin" / "plugin.json",
)
SKILLS = (
    "jaipilot-clean-java",
    "jaipilot-generate-tests",
    "jaipilot-review-diff",
)
FORBIDDEN_PATHS = (
    ROOT / "src",
    ROOT / "pom.xml",
    ROOT / "mvnw",
    ROOT / "mvnw.cmd",
    ROOT / ".mvn",
    PLUGIN / ".mcp.json",
    PLUGIN / ".app.json",
    PLUGIN / "bin",
    PLUGIN / "hooks",
    PLUGIN / "libexec",
    PLUGIN / "scripts",
)
ALLOWED_PLUGIN_ROOTS = {
    ".claude-plugin",
    ".codex-plugin",
    "README.md",
    "assets",
    "plugin.json",
    "skills",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load(path: Path) -> dict[str, object]:
    value = json.loads(read(path))
    require(isinstance(value, dict), f"{path.relative_to(ROOT)}: expected an object")
    return value


def version() -> str:
    value = read(ROOT / "VERSION").strip()
    require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", value) is not None,
            "VERSION must contain one semantic version")
    return value


def validate_manifests(expected_version: str) -> None:
    for path in PLUGIN_FILES:
        manifest = load(path)
        label = path.relative_to(ROOT)
        require(manifest.get("name") == "jaipilot", f"{label}: name must be jaipilot")
        require(manifest.get("version") == expected_version,
                f"{label}: version must match VERSION ({expected_version})")
        description = manifest.get("description")
        require(isinstance(description, str) and 20 <= len(description) <= 140,
                f"{label}: description must contain 20-140 characters")

    codex = load(PLUGIN / ".codex-plugin" / "plugin.json")
    require(codex.get("skills") == "./skills/", "Codex manifest must expose ./skills/")
    interface = codex.get("interface")
    require(isinstance(interface, dict), "Codex interface is required")
    prompts = interface.get("defaultPrompt")
    require(isinstance(prompts, list) and len(prompts) == 3,
            "Codex interface must contain three starter prompts")


def validate_skills() -> None:
    skills_root = PLUGIN / "skills"
    actual = sorted(path.name for path in skills_root.iterdir() if path.is_dir())
    require(actual == list(SKILLS), f"Expected exactly {list(SKILLS)}; found {actual}")

    for name in SKILLS:
        directory = skills_root / name
        skill = read(directory / "SKILL.md")
        match = re.match(r"^---\n([\s\S]*?)\n---\n", skill)
        require(match is not None, f"{name}: YAML frontmatter is required")
        keys = re.findall(r"^([a-z_]+):", match.group(1), re.MULTILINE)
        require(keys == ["name", "description"],
                f"{name}: frontmatter must contain only name and description")
        require(re.search(rf"^name:\s*{re.escape(name)}\s*$", match.group(1), re.MULTILINE)
                is not None, f"{name}: name must match its directory")
        require(re.search(r"^description:\s*\S.+$", match.group(1), re.MULTILINE)
                is not None, f"{name}: description is required")
        require(len(skill.splitlines()) <= 120, f"{name}: keep SKILL.md at or below 120 lines")
        require(re.search(r"\b(?:TODO|TBD)\b", skill, re.IGNORECASE) is None,
                f"{name}: unresolved placeholder found")
        require(re.search(
            r"jaipilot_|jaipilot-toolkit|\.mcp\.json|bin/jaipilot|proof receipt|"
            r"127\.0\.0\.1|localhost",
            skill,
            re.IGNORECASE,
        ) is None, f"{name}: runtime-era instruction found")

        metadata = read(directory / "agents" / "openai.yaml")
        for field in ("display_name", "short_description", "default_prompt"):
            require(re.search(rf"^\s*{field}:\s*.+$", metadata, re.MULTILINE) is not None,
                    f"{name}: agents/openai.yaml is missing {field}")
        require("$" + name in metadata, f"{name}: default prompt must mention the skill")


def validate_marketplaces(expected_version: str) -> None:
    codex = load(ROOT / ".agents" / "plugins" / "marketplace.json")
    entries = codex.get("plugins")
    require(isinstance(entries, list) and len(entries) == 1,
            "Codex marketplace must contain exactly one plugin")
    entry = entries[0]
    require(isinstance(entry, dict) and entry.get("name") == "jaipilot",
            "Codex marketplace must publish jaipilot")
    source = entry.get("source")
    require(isinstance(source, dict) and source.get("path") == "./plugins/jaipilot",
            "Codex marketplace source must be ./plugins/jaipilot")

    claude = load(ROOT / ".claude-plugin" / "marketplace.json")
    entries = claude.get("plugins")
    require(isinstance(entries, list) and len(entries) == 1,
            "Claude marketplace must contain exactly one plugin")
    entry = entries[0]
    require(isinstance(entry, dict)
            and entry.get("name") == "jaipilot"
            and entry.get("source") == "./plugins/jaipilot"
            and entry.get("version") == expected_version,
            "Claude marketplace name, source, and version must align")


def validate_lean_payload() -> tuple[int, int]:
    for path in FORBIDDEN_PATHS:
        require(not path.exists(), f"Skills-only product must not contain {path.relative_to(ROOT)}")
    require({path.name for path in PLUGIN.iterdir()} == ALLOWED_PLUGIN_ROOTS,
            "Plugin root contains an unexpected runtime or auxiliary entry")

    files = [path for path in PLUGIN.rglob("*") if path.is_file()]
    require(not any(path.is_symlink() for path in PLUGIN.rglob("*")),
            "Plugin must not contain symbolic links")
    require(not any(path.suffix in {".class", ".jar", ".py", ".sh"} for path in files),
            "Plugin must contain no executable source or runtime payload")
    require(not any(path.stat().st_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
                    for path in files),
            "Plugin files must not be executable")
    total = sum(path.stat().st_size for path in files)
    require(total <= 131_072, f"Plugin payload must remain at or below 128 KiB; found {total}")
    return len(files), total


def main() -> None:
    expected_version = version()
    validate_manifests(expected_version)
    validate_skills()
    validate_marketplaces(expected_version)
    count, size = validate_lean_payload()
    print(f"Validated JAIPilot {expected_version}: 3 skills, {count} files, {size} bytes, no runtime.")


if __name__ == "__main__":
    main()
