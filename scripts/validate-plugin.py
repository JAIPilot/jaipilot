#!/usr/bin/env python3
"""Validate JAIPilot's lean skills and remote-execution plugin."""

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
    "jaipilot-fast-execution",
    "jaipilot-generate-tests",
    "jaipilot-maintainer-intent",
    "jaipilot-optimize-java",
    "jaipilot-remote-java",
    "jaipilot-review-diff",
)
FORBIDDEN_PATHS = (
    ROOT / "src",
    ROOT / "pom.xml",
    ROOT / "mvnw",
    ROOT / "mvnw.cmd",
    ROOT / ".mvn",
    PLUGIN / ".app.json",
    PLUGIN / "bin",
    PLUGIN / "hooks",
    PLUGIN / "libexec",
    PLUGIN / "scripts",
)
ALLOWED_PLUGIN_ROOTS = {
    ".claude-plugin",
    ".codex-plugin",
    ".mcp.json",
    "README.md",
    "assets",
    "plugin.json",
    "skills",
}
MCP_API_URL = "https://api.jaipilot.com/functions/v1/jaipilot-cloud/mcp"
MCP_REGISTRY_SCHEMA = (
    "https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json"
)
MCP_REGISTRY_NAME = "io.github.JAIPilot/jaipilot"
REPOSITORY_URL = "https://github.com/JAIPilot/jaipilot"
REPOSITORY_ID = "1181029327"
TAGLINE = "Ship better Java with your coding agent."
PRIVACY_URL = "https://github.com/JAIPilot/jaipilot/blob/main/PRIVACY.md"
TERMS_URL = "https://github.com/JAIPilot/jaipilot/blob/main/TERMS.md"
RETIRED_BEHAVIOR_LOCK = re.compile(
    r"behaviou?r[- ]lock|lock(?:ed|ing)? (?:observable )?behaviou?r|"
    r"behaviou?r (?:baseline|candidate)|characterization",
    re.IGNORECASE,
)


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
        require(description == TAGLINE,
                f"{label}: description must use the canonical tagline")

    codex = load(PLUGIN / ".codex-plugin" / "plugin.json")
    require(codex.get("skills") == "./skills/", "Codex manifest must expose ./skills/")
    codex_mcp = codex.get("mcpServers")
    require(isinstance(codex_mcp, dict) and set(codex_mcp) == {"jaipilot-remote"},
            "Codex manifest must inline only the bundled Codex MCP binding")
    require(load(PLUGIN / "plugin.json").get("mcpServers") == "./.mcp.json",
            "Compatibility manifest must expose the bundled .mcp.json")
    interface = codex.get("interface")
    require(isinstance(interface, dict), "Codex interface is required")
    prompts = interface.get("defaultPrompt")
    require(isinstance(prompts, list) and len(prompts) == 3,
            "Codex interface must contain three starter prompts")
    require(interface.get("privacyPolicyURL") == PRIVACY_URL,
            "Codex interface must publish the canonical privacy policy URL")
    require(interface.get("termsOfServiceURL") == TERMS_URL,
            "Codex interface must publish the canonical terms URL")
    require(interface.get("shortDescription") == TAGLINE,
            "Codex interface must use the canonical tagline")


def validate_skills() -> None:
    skills_root = PLUGIN / "skills"
    actual = sorted(path.name for path in skills_root.iterdir() if path.is_dir())
    require(actual == list(SKILLS), f"Expected exactly {list(SKILLS)}; found {actual}")
    known_skills = set(SKILLS)

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
        description = re.search(r"^description:\s*(\S.+)$", match.group(1), re.MULTILINE)
        require(description is not None, f"{name}: description is required")
        require(40 <= len(description.group(1)) <= 1024,
                f"{name}: description must contain 40-1024 characters")
        require(len(skill.splitlines()) <= 120, f"{name}: keep SKILL.md at or below 120 lines")
        require(re.search(r"\b(?:TODO|TBD)\b", skill, re.IGNORECASE) is None,
                f"{name}: unresolved placeholder found")
        require(re.search(
            r"jaipilot-toolkit|bin/jaipilot|proof receipt|127\.0\.0\.1|localhost|"
            r"JAIPILOT_MODE|JAIPILOT_BASE_SHA|/workspace/repo|/mnt/session|package-result",
            skill,
            re.IGNORECASE,
        ) is None, f"{name}: local plugin contains a retired or cloud-only instruction")
        referenced_skills = set(re.findall(r"`(jaipilot-[a-z0-9-]+)`", skill))
        require(referenced_skills <= known_skills,
                f"{name}: references unknown skills {sorted(referenced_skills - known_skills)}")

        metadata = read(directory / "agents" / "openai.yaml")
        for field in ("display_name", "short_description", "default_prompt"):
            require(re.search(rf"^\s*{field}:\s*.+$", metadata, re.MULTILINE) is not None,
                    f"{name}: agents/openai.yaml is missing {field}")
        require("$" + name in metadata, f"{name}: default prompt must mention the skill")
        short_description = re.search(
            r'^\s*short_description:\s*"([^"]+)"\s*$', metadata, re.MULTILINE
        )
        require(short_description is not None
                and 25 <= len(short_description.group(1)) <= 64,
                f"{name}: short_description must contain 25-64 characters")


def validate_retired_features() -> None:
    for path in PLUGIN.rglob("*"):
        if not path.is_file() or path.suffix not in {".json", ".md", ".ts", ".yaml", ".yml"}:
            continue
        require(RETIRED_BEHAVIOR_LOCK.search(read(path)) is None,
                f"{path.relative_to(ROOT)}: retired behavior-lock workflow found")


def validate_mcp_server(
        server: object,
        label: str,
) -> None:
    require(isinstance(server, dict), "jaipilot-remote must be an object")
    require(server == {"type": "http", "url": MCP_API_URL},
            f"{label}: jaipilot-remote must contain only the hosted OAuth MCP URL")


def validate_mcp() -> None:
    claude = load(PLUGIN / ".mcp.json").get("mcpServers")
    require(isinstance(claude, dict) and set(claude) == {"jaipilot-remote"},
            "Claude MCP config must contain only jaipilot-remote")
    validate_mcp_server(
        claude["jaipilot-remote"],
        ".mcp.json",
    )
    codex = load(PLUGIN / ".codex-plugin" / "plugin.json").get("mcpServers")
    require(isinstance(codex, dict) and set(codex) == {"jaipilot-remote"},
            "Codex manifest must contain only jaipilot-remote")
    validate_mcp_server(
        codex["jaipilot-remote"],
        ".codex-plugin/plugin.json",
    )
    distributed = "\n".join(
        read(path) for path in PLUGIN.rglob("*")
        if path.is_file() and path.suffix in {".json", ".md", ".yaml", ".yml"}
    )
    require(re.search(
        r"JAIPILOT_CLOUD_TRIGGER_SECRET|DAYTONA|GITHUB_APP_PRIVATE_KEY|sk-ant-",
        distributed,
        re.IGNORECASE,
    ) is None, "Plugin must not expose provider details or credential configuration")


def validate_mcp_registry(expected_version: str) -> None:
    server = load(ROOT / "server.json")
    require(server.get("$schema") == MCP_REGISTRY_SCHEMA,
            "server.json must use the pinned MCP Registry schema")
    require(server.get("name") == MCP_REGISTRY_NAME,
            "server.json must use the JAIPilot GitHub namespace")
    require(server.get("title") == "JAIPilot Remote",
            "server.json must publish the JAIPilot Remote title")
    description = server.get("description")
    require(description == TAGLINE,
            "server.json description must use the canonical tagline")
    require(server.get("version") == expected_version,
            f"server.json version must match VERSION ({expected_version})")
    require(server.get("websiteUrl") == REPOSITORY_URL,
            "server.json must link to the public product documentation")
    require(server.get("repository") == {
        "url": REPOSITORY_URL,
        "source": "github",
        "id": REPOSITORY_ID,
    }, "server.json must publish the stable public repository identity")
    require(server.get("remotes") == [{
        "type": "streamable-http",
        "url": MCP_API_URL,
    }], "server.json must publish only the hosted Streamable HTTP endpoint")
    icons = server.get("icons")
    require(isinstance(icons, list) and len(icons) == 1,
            "server.json must publish one JAIPilot icon")
    icon = icons[0]
    require(isinstance(icon, dict)
            and icon.get("src") == (
                "https://raw.githubusercontent.com/JAIPilot/jaipilot/main/"
                "plugins/jaipilot/assets/jaipilot-logo.svg"
            )
            and icon.get("mimeType") == "image/svg+xml"
            and icon.get("sizes") == ["any"],
            "server.json must publish the canonical scalable JAIPilot icon")


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
    metadata = claude.get("metadata")
    require(isinstance(metadata, dict) and metadata.get("description") == TAGLINE,
            "Claude marketplace metadata must use the canonical tagline")
    entries = claude.get("plugins")
    require(isinstance(entries, list) and len(entries) == 1,
            "Claude marketplace must contain exactly one plugin")
    entry = entries[0]
    require(isinstance(entry, dict)
            and entry.get("name") == "jaipilot"
            and entry.get("source") == "./plugins/jaipilot"
            and entry.get("description") == TAGLINE
            and entry.get("version") == expected_version,
            "Claude marketplace name, source, description, and version must align")


def validate_tagline() -> None:
    for path in (ROOT / "README.md", PLUGIN / "README.md"):
        require(f"**{TAGLINE}**" in read(path),
                f"{path.relative_to(ROOT)} must use the canonical tagline")


def validate_lean_payload() -> tuple[int, int]:
    for path in FORBIDDEN_PATHS:
        require(not path.exists(), f"Plugin must not contain {path.relative_to(ROOT)}")
    require({path.name for path in PLUGIN.iterdir()} == ALLOWED_PLUGIN_ROOTS,
            "Plugin root contains an unexpected entry")

    files = [path for path in PLUGIN.rglob("*") if path.is_file()]
    require(not any(path.is_symlink() for path in PLUGIN.rglob("*")),
            "Plugin must not contain symbolic links")
    require(not any(path.suffix in {".class", ".jar", ".py", ".sh"} for path in files),
            "Plugin must contain no binary, shell, or package-manager runtime payload")
    require(not any(path.suffix == ".ts" for path in files),
            "Hosted MCP plugin must not contain a local TypeScript runtime")
    require(not any(path.stat().st_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
                    for path in files),
            "Plugin files must not be executable")
    total = sum(path.stat().st_size for path in files)
    require(total <= 196_608, f"Plugin payload must remain at or below 192 KiB; found {total}")
    return len(files), total


def main() -> None:
    expected_version = version()
    validate_manifests(expected_version)
    validate_skills()
    validate_retired_features()
    validate_mcp()
    validate_mcp_registry(expected_version)
    validate_marketplaces(expected_version)
    validate_tagline()
    count, size = validate_lean_payload()
    print(
        f"Validated JAIPilot {expected_version}: {len(SKILLS)} skills, 8 remote tools, "
        f"{count} files, {size} bytes."
    )


if __name__ == "__main__":
    main()
