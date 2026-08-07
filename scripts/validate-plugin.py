#!/usr/bin/env python3
"""Validate JAIPilot's shared plugin, marketplaces, skills, and release version alignment."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
PLUGIN_FILES = (
    "plugins/jaipilot/plugin.json",
    "plugins/jaipilot/.codex-plugin/plugin.json",
    "plugins/jaipilot/.claude-plugin/plugin.json",
)
SKILLS = ("jaipilot-generate-tests", "jaipilot-clean-java", "jaipilot-review-diff")


def text(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def payload(relative_path: str) -> dict[str, object]:
    value = json.loads(text(relative_path))
    require(isinstance(value, dict), f"{relative_path}: expected a JSON object")
    return value


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def project_version() -> str:
    match = re.search(r"<revision>([^<]+)</revision>", text("pom.xml"))
    require(match is not None, "pom.xml: revision is required")
    return match.group(1)


def main() -> None:
    version = project_version()
    for plugin_file in PLUGIN_FILES:
        manifest = payload(plugin_file)
        require(manifest.get("name") == "jaipilot", f"{plugin_file}: name must be jaipilot")
        require(
            manifest.get("version") == version,
            f"{plugin_file}: version must match pom.xml ({version})",
        )
        description = manifest.get("description")
        require(
            isinstance(description, str) and len(description) >= 20,
            f"{plugin_file}: description is missing or too short",
        )

    codex = payload("plugins/jaipilot/.codex-plugin/plugin.json")
    interface = codex.get("interface")
    require(isinstance(interface, dict), "Codex plugin interface is required")
    prompts = interface.get("defaultPrompt")
    require(
        isinstance(prompts, list) and len(prompts) >= 2,
        "Codex starter prompts are required",
    )
    require(
        f'VERSION="{version}"' in text("plugins/jaipilot/bin/jaipilot"),
        "Plugin bootstrap version must match pom.xml",
    )
    require(
        (ROOT / "plugins/jaipilot/libexec/install.sh").is_file(),
        "Plugin-local installer is required",
    )
    hooks = payload("plugins/jaipilot/hooks/hooks.json")
    stop_hooks = hooks.get("hooks", {}).get("Stop") if isinstance(hooks.get("hooks"), dict) else None
    require(isinstance(stop_hooks, list) and len(stop_hooks) == 1, "One automatic Stop hook is required")
    command = stop_hooks[0].get("hooks", [{}])[0].get("command") if isinstance(stop_hooks[0], dict) else None
    require(
        isinstance(command, str)
        and "${CLAUDE_PLUGIN_ROOT}/bin/jaipilot" in command
        and "hook-stop" in command,
        "Stop hook must use the portable plugin runner",
    )

    codex_marketplace = payload(".agents/plugins/marketplace.json")
    codex_entries = codex_marketplace.get("plugins")
    require(isinstance(codex_entries, list), "Codex marketplace plugins are required")
    require(
        any(
            isinstance(entry, dict)
            and entry.get("name") == "jaipilot"
            and isinstance(entry.get("source"), dict)
            and entry["source"].get("path") == "./plugins/jaipilot"
            for entry in codex_entries
        ),
        "Codex marketplace must publish ./plugins/jaipilot",
    )

    claude_marketplace = payload(".claude-plugin/marketplace.json")
    claude_entries = claude_marketplace.get("plugins")
    require(isinstance(claude_entries, list), "Claude marketplace plugins are required")
    require(
        any(
            isinstance(entry, dict)
            and entry.get("name") == "jaipilot"
            and entry.get("source") == "./plugins/jaipilot"
            and entry.get("version") == version
            for entry in claude_entries
        ),
        "Claude marketplace must publish the current ./plugins/jaipilot version",
    )

    for skill_name in SKILLS:
        directory = f"plugins/jaipilot/skills/{skill_name}"
        skill = text(f"{directory}/SKILL.md")
        frontmatter = re.match(r"^---\n([\s\S]*?)\n---\n", skill)
        require(frontmatter is not None, f"{directory}/SKILL.md: frontmatter is required")
        require(
            re.search(rf"^name:\s*{re.escape(skill_name)}\s*$", frontmatter.group(1), re.MULTILINE)
            is not None,
            f"{directory}/SKILL.md: name must match its directory",
        )
        require(
            re.search(r"^description:\s*\S.+$", frontmatter.group(1), re.MULTILINE) is not None,
            f"{directory}/SKILL.md: description is required",
        )
        require(
            re.search(r"\b(?:TODO|TBD)\b", skill, re.IGNORECASE) is None,
            f"{directory}/SKILL.md: unresolved placeholder found",
        )
        openai = text(f"{directory}/agents/openai.yaml")
        for field in ("display_name", "short_description", "default_prompt"):
            require(
                re.search(rf"^\s*{field}:\s*.+$", openai, re.MULTILINE) is not None,
                f"{directory}/agents/openai.yaml: {field} is required",
            )
        require(
            f"${skill_name}" in openai,
            f"{directory}/agents/openai.yaml: default_prompt must mention ${skill_name}",
        )

    print(f"Validated JAIPilot plugin {version} and {len(SKILLS)} Agent Skills.")


if __name__ == "__main__":
    main()
