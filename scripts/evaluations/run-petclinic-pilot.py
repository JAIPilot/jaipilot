#!/usr/bin/env python3
"""Run the frozen JAIPilot Petclinic A/B pilot and retain every trial."""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import shutil
import signal
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


REPOSITORY = "https://github.com/spring-petclinic/spring-framework-petclinic.git"
REVISION = "233dfcd06db3fb0505c2accc106f45ef72670990"
PLUGIN = "jaipilot@jaipilot"
PLUGIN_VERSION = "4.0.3"
MODEL = "gpt-5.6-sol"
REASONING = "xhigh"
SERVICE_TIER = "fast"
RANDOM_SEED = 20260808
DEFAULT_JAVA_HOME = Path(
    "/Users/surajkrishnanrajan/Library/Java/JavaVirtualMachines/"
    "corretto-17.0.13/Contents/Home"
)
ROOT = Path(__file__).resolve().parents[2]
EVALUATION = ROOT / "evaluations" / "petclinic-pilot"


@dataclass(frozen=True)
class Task:
    name: str
    hidden_class: str
    allowed_paths: tuple[str, ...]

    @property
    def prompt(self) -> Path:
        return EVALUATION / "tasks" / f"{self.name}.md"

    @property
    def hidden(self) -> Path:
        return EVALUATION / "hidden" / self.name


TASKS = {
    task.name: task
    for task in (
        Task(
            "visit-scheduling",
            "VisitSchedulingAcceptanceTests",
            (
                "src/main/java/org/springframework/samples/petclinic/service/",
                "src/main/java/org/springframework/samples/petclinic/web/VisitController.java",
                "src/test/java/org/springframework/samples/petclinic/service/",
                "src/test/java/org/springframework/samples/petclinic/web/",
            ),
        ),
        Task(
            "pet-transfer",
            "PetTransferAcceptanceTests",
            (
                "src/main/java/org/springframework/samples/petclinic/model/Owner.java",
                "src/main/java/org/springframework/samples/petclinic/model/Pet.java",
                "src/main/java/org/springframework/samples/petclinic/service/",
                "src/test/java/org/springframework/samples/petclinic/model/",
                "src/test/java/org/springframework/samples/petclinic/service/",
            ),
        ),
        Task(
            "upcoming-visits",
            "UpcomingVisitsAcceptanceTests",
            (
                "src/main/java/org/springframework/samples/petclinic/model/Pet.java",
                "src/main/java/org/springframework/samples/petclinic/service/",
                "src/test/java/org/springframework/samples/petclinic/model/",
                "src/test/java/org/springframework/samples/petclinic/service/",
            ),
        ),
        Task(
            "vet-specialty",
            "VetSpecialtyAcceptanceTests",
            (
                "src/main/java/org/springframework/samples/petclinic/service/",
                "src/test/java/org/springframework/samples/petclinic/service/",
            ),
        ),
    )
}


def command(
    arguments: list[str],
    *,
    cwd: Path | None = None,
    environment: dict[str, str] | None = None,
    check: bool = True,
    capture: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        arguments,
        cwd=cwd,
        env=environment,
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def java_environment(java_home: Path) -> dict[str, str]:
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    return environment


def ensure_seed(work_root: Path, java_home: Path) -> Path:
    seed = work_root / "seed"
    if not (seed / ".git").is_dir():
        command(["git", "clone", REPOSITORY, str(seed)], capture=False)
    command(["git", "fetch", "origin", "--tags"], cwd=seed)
    command(["git", "checkout", "--detach", REVISION], cwd=seed)
    command(["git", "branch", "-f", "main", REVISION], cwd=seed)
    actual = command(["git", "rev-parse", "HEAD"], cwd=seed).stdout.strip()
    if actual != REVISION:
        raise RuntimeError(f"Expected seed {REVISION}, received {actual}")
    warm_log = work_root / "baseline-verify.log"
    if not warm_log.exists():
        result = command(
            ["./mvnw", "-B", "clean", "verify"],
            cwd=seed,
            environment=java_environment(java_home),
            check=False,
        )
        warm_log.write_text(result.stdout + result.stderr, encoding="utf-8")
        if result.returncode != 0:
            raise RuntimeError(f"Frozen baseline failed; see {warm_log}")
    return seed


def plugin_present() -> bool:
    listing = command(["codex", "plugin", "list"], check=False).stdout
    return any(
        line.strip().startswith(PLUGIN) and "installed, enabled" in line
        for line in listing.splitlines()
    )


def set_plugin(enabled: bool) -> Path | None:
    if plugin_present():
        command(["codex", "plugin", "remove", PLUGIN, "--json"])
    if enabled:
        added = command(["codex", "plugin", "add", PLUGIN, "--json"])
        payload = json.loads(added.stdout)
        if payload.get("version") != PLUGIN_VERSION:
            raise RuntimeError(f"Expected JAIPilot {PLUGIN_VERSION}, received {payload}")
        installed = Path(payload["installedPath"])
        if (installed / "hooks").exists():
            raise RuntimeError("Treatment plugin unexpectedly contains automatic hooks")
        return installed
    return None


def ensure_runtime(plugin_root: Path, environment: dict[str, str]) -> None:
    launcher = plugin_root / "bin" / "jaipilot"
    result = command(
        [str(launcher), "version"],
        environment=environment,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "JAIPilot runtime prewarm failed before the measured trials:\n"
            + result.stdout
            + result.stderr
        )
    payload = (
        Path(environment["JAIPILOT_RUNTIME_HOME"])
        / "versions"
        / PLUGIN_VERSION
        / "jaipilot-toolkit.jar"
    )
    if not payload.is_file():
        raise RuntimeError(f"JAIPilot runtime prewarm did not create {payload}")


def clone_trial(seed: Path, destination: Path) -> None:
    if destination.exists():
        destination.rename(destination.with_name(destination.name + f".incomplete-{int(time.time())}"))
    destination.parent.mkdir(parents=True, exist_ok=True)
    command(["git", "clone", "--shared", "--branch", "main", str(seed), str(destination)])
    command(["git", "switch", "-c", "trial"], cwd=destination)


def run_agent(
    trial: Path,
    result_directory: Path,
    prompt: str,
    environment: dict[str, str],
    timeout_seconds: int,
    plugin_root: Path | None,
) -> tuple[int, float, bool]:
    transcript = result_directory / "agent.jsonl"
    diagnostics = result_directory / "agent.stderr.log"
    invocation = [
        "codex",
        "exec",
        "--ephemeral",
        "--json",
        "--color",
        "never",
        "--sandbox",
        "danger-full-access",
        "--cd",
        str(trial),
        "--model",
        MODEL,
        "--config",
        f'model_reasoning_effort="{REASONING}"',
        "--config",
        f'service_tier="{SERVICE_TIER}"',
    ]
    if plugin_root is not None:
        mcp_launcher = plugin_root / "bin" / "jaipilot-mcp"
        invocation.extend(
            [
                "--config",
                "mcp_servers.jaipilot.command=" + json.dumps(str(mcp_launcher)),
            ]
        )
        for variable in (
            "JAVA_HOME",
            "JAIPILOT_RUNTIME_HOME",
            "JAIPILOT_STATE_HOME",
            "JAIPILOT_DASHBOARD_DISABLED",
        ):
            invocation.extend(
                [
                    "--config",
                    f"mcp_servers.jaipilot.env.{variable}="
                    + json.dumps(environment[variable]),
                ]
            )
    invocation.append("-")
    started = time.monotonic()
    timed_out = False
    with transcript.open("wb") as stdout, diagnostics.open("wb") as stderr:
        process = subprocess.Popen(
            invocation,
            cwd=trial,
            env=environment,
            stdin=subprocess.PIPE,
            stdout=stdout,
            stderr=stderr,
            start_new_session=True,
        )
        assert process.stdin is not None
        process.stdin.write(prompt.encode())
        process.stdin.close()
        try:
            status = process.wait(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(process.pid, signal.SIGTERM)
            try:
                status = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                status = process.wait(timeout=10)
    return status, time.monotonic() - started, timed_out


def changed_paths(trial: Path) -> list[str]:
    tracked = command(["git", "diff", "--name-only", "HEAD"], cwd=trial).stdout.splitlines()
    untracked = command(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=trial
    ).stdout.splitlines()
    return sorted(set(tracked + untracked))


def save_patch(trial: Path, destination: Path, paths: list[str]) -> None:
    untracked = command(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=trial
    ).stdout.splitlines()
    if untracked:
        command(["git", "add", "-N", "--", *untracked], cwd=trial)
    patch = command(["git", "diff", "--binary", "HEAD"], cwd=trial).stdout
    destination.write_text(patch, encoding="utf-8")
    command(["git", "reset", "--quiet"], cwd=trial)
    if paths and not patch:
        raise RuntimeError("Changed paths exist but the retained patch is empty")


def copy_hidden_tests(task: Task, trial: Path) -> None:
    for source in sorted(task.hidden.rglob("*.java")):
        relative = source.relative_to(task.hidden)
        destination = trial / "src" / "test" / "java" / relative
        if destination.exists():
            raise RuntimeError(f"Agent created reserved hidden-test path {destination}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)


def run_logged(
    arguments: list[str],
    *,
    cwd: Path,
    environment: dict[str, str],
    log: Path,
    timeout_seconds: int,
) -> tuple[int, float, bool]:
    started = time.monotonic()
    timed_out = False
    with log.open("wb") as output:
        process = subprocess.Popen(
            arguments,
            cwd=cwd,
            env=environment,
            stdout=output,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            status = process.wait(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(process.pid, signal.SIGTERM)
            try:
                status = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                status = process.wait(timeout=10)
    return status, time.monotonic() - started, timed_out


def scope_allowed(task: Task, path: str) -> bool:
    return any(path == allowed or path.startswith(allowed) for allowed in task.allowed_paths)


def transcript_metrics(path: Path) -> dict[str, int]:
    metrics = {
        "inputTokens": 0,
        "cachedInputTokens": 0,
        "outputTokens": 0,
        "jaipilotReferences": 0,
        "buildCommandReferences": 0,
    }
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "jaipilot_" in line or "JAIPilot" in line:
            metrics["jaipilotReferences"] += 1
        if "mvnw" in line or "mvn " in line:
            metrics["buildCommandReferences"] += 1
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        usage = event.get("usage")
        if isinstance(usage, dict):
            for source, target in (
                ("input_tokens", "inputTokens"),
                ("cached_input_tokens", "cachedInputTokens"),
                ("output_tokens", "outputTokens"),
            ):
                value = usage.get(source)
                if isinstance(value, int):
                    metrics[target] = max(metrics[target], value)
    return metrics


def diff_counts(trial: Path) -> tuple[int, int]:
    untracked = command(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=trial
    ).stdout.splitlines()
    if untracked:
        command(["git", "add", "-N", "--", *untracked], cwd=trial)
    added = 0
    deleted = 0
    for line in command(["git", "diff", "--numstat", "HEAD"], cwd=trial).stdout.splitlines():
        before, after, _ = line.split("\t", 2)
        if before.isdigit():
            added += int(before)
        if after.isdigit():
            deleted += int(after)
    command(["git", "reset", "--quiet"], cwd=trial)
    return added, deleted


def evaluate_trial(
    task: Task,
    trial: Path,
    result_directory: Path,
    environment: dict[str, str],
    agent_status: int,
    agent_seconds: float,
    agent_timed_out: bool,
    arm: str,
    repetition: int,
) -> dict[str, Any]:
    paths = changed_paths(trial)
    save_patch(trial, result_directory / "agent.patch", paths)
    added, deleted = diff_counts(trial)
    scope_violations = [path for path in paths if not scope_allowed(task, path)]
    diff_check = command(["git", "diff", "--check", "HEAD"], cwd=trial, check=False)
    (result_directory / "diff-check.log").write_text(
        diff_check.stdout + diff_check.stderr, encoding="utf-8"
    )
    hidden_reserved = False
    try:
        copy_hidden_tests(task, trial)
    except RuntimeError as exception:
        hidden_reserved = True
        (result_directory / "hidden-copy-error.txt").write_text(str(exception), encoding="utf-8")

    hidden_status = 1
    hidden_seconds = 0.0
    hidden_timed_out = False
    verify_status = 1
    verify_seconds = 0.0
    verify_timed_out = False
    if not hidden_reserved:
        hidden_status, hidden_seconds, hidden_timed_out = run_logged(
            ["./mvnw", "-B", f"-Dtest={task.hidden_class}", "test"],
            cwd=trial,
            environment=environment,
            log=result_directory / "hidden-tests.log",
            timeout_seconds=300,
        )
        verify_status, verify_seconds, verify_timed_out = run_logged(
            ["./mvnw", "-B", "clean", "verify"],
            cwd=trial,
            environment=environment,
            log=result_directory / "full-verify.log",
            timeout_seconds=600,
        )

    metrics = transcript_metrics(result_directory / "agent.jsonl")
    accepted = (
        hidden_status == 0
        and verify_status == 0
        and diff_check.returncode == 0
        and not scope_violations
        and bool(paths)
    )
    return {
        "schemaVersion": 1,
        "task": task.name,
        "arm": arm,
        "repetition": repetition,
        "accepted": accepted,
        "agentExit": agent_status,
        "agentTimedOut": agent_timed_out,
        "agentSeconds": round(agent_seconds, 3),
        "hiddenTestsPassed": hidden_status == 0,
        "hiddenTestsTimedOut": hidden_timed_out,
        "hiddenTestSeconds": round(hidden_seconds, 3),
        "fullVerifyPassed": verify_status == 0,
        "fullVerifyTimedOut": verify_timed_out,
        "fullVerifySeconds": round(verify_seconds, 3),
        "diffCheckPassed": diff_check.returncode == 0,
        "scopePassed": not scope_violations,
        "scopeViolations": scope_violations,
        "changedPaths": paths,
        "linesAdded": added,
        "linesDeleted": deleted,
        **metrics,
    }


def percentile95(values: list[float]) -> float | None:
    if not values:
        return None
    return sorted(values)[math.ceil(0.95 * len(values)) - 1]


def write_summary(work_root: Path) -> None:
    results = []
    for path in sorted((work_root / "results").glob("*/result.json")):
        results.append(json.loads(path.read_text(encoding="utf-8")))
    arms: dict[str, Any] = {}
    for arm in ("baseline", "treatment"):
        selected = [result for result in results if result["arm"] == arm]
        seconds = [float(result["agentSeconds"]) for result in selected]
        arms[arm] = {
            "trials": len(selected),
            "accepted": sum(bool(result["accepted"]) for result in selected),
            "acceptanceRate": (
                sum(bool(result["accepted"]) for result in selected) / len(selected)
                if selected else None
            ),
            "medianAgentSeconds": statistics.median(seconds) if seconds else None,
            "p95AgentSeconds": percentile95(seconds),
            "medianInputTokens": (
                statistics.median(result["inputTokens"] for result in selected)
                if selected else None
            ),
            "medianOutputTokens": (
                statistics.median(result["outputTokens"] for result in selected)
                if selected else None
            ),
            "jaipilotReferences": sum(result["jaipilotReferences"] for result in selected),
        }
    summary = {
        "schemaVersion": 1,
        "repository": REPOSITORY,
        "revision": REVISION,
        "model": MODEL,
        "reasoning": REASONING,
        "serviceTier": SERVICE_TIER,
        "pluginVersion": PLUGIN_VERSION,
        "results": results,
        "arms": arms,
    }
    (work_root / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )


def selected_tasks(value: str) -> list[Task]:
    names = list(TASKS) if value == "all" else value.split(",")
    unknown = set(names) - set(TASKS)
    if unknown:
        raise ValueError(f"Unknown tasks: {sorted(unknown)}")
    return [TASKS[name] for name in names]


def trial_schedule(tasks: Iterable[Task], repetitions: int, arms: list[str]) -> list[tuple[Task, int, str]]:
    trials = [
        (task, repetition, arm)
        for task in tasks
        for repetition in range(1, repetitions + 1)
        for arm in arms
    ]
    random.Random(RANDOM_SEED).shuffle(trials)
    return trials


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-root", type=Path, required=True)
    parser.add_argument("--tasks", default="all")
    parser.add_argument("--arms", default="baseline,treatment")
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--timeout-seconds", type=int, default=1200)
    parser.add_argument("--java-home", type=Path, default=DEFAULT_JAVA_HOME)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    work_root = arguments.work_root.expanduser().resolve()
    work_root.mkdir(parents=True, exist_ok=True)
    tasks = selected_tasks(arguments.tasks)
    arms = arguments.arms.split(",")
    if not set(arms) <= {"baseline", "treatment"}:
        raise ValueError("Arms must be baseline and/or treatment")
    if arguments.repetitions < 1:
        raise ValueError("Repetitions must be positive")
    if not (arguments.java_home / "bin" / "java").is_file():
        raise ValueError(f"Java home is unavailable: {arguments.java_home}")

    results_root = work_root / "results"
    results_root.mkdir(exist_ok=True)
    seed = ensure_seed(work_root, arguments.java_home)
    command(["codex", "plugin", "marketplace", "upgrade", "jaipilot", "--json"])
    environment = java_environment(arguments.java_home)
    environment["JAIPILOT_RUNTIME_HOME"] = str(work_root / "jaipilot-runtime")
    environment["JAIPILOT_DASHBOARD_DISABLED"] = "1"
    plugin_root = set_plugin(True)
    assert plugin_root is not None
    ensure_runtime(plugin_root, environment)

    try:
        for index, (task, repetition, arm) in enumerate(
            trial_schedule(tasks, arguments.repetitions, arms), start=1
        ):
            trial_id = f"{task.name}-r{repetition}-{arm}"
            result_directory = results_root / trial_id
            result_path = result_directory / "result.json"
            if result_path.exists():
                print(f"[{index}] skip {trial_id}: result exists", flush=True)
                continue
            result_directory.mkdir(parents=True, exist_ok=True)
            trial_plugin_root = set_plugin(arm == "treatment")
            trial = work_root / "trials" / trial_id
            clone_trial(seed, trial)
            trial_environment = environment.copy()
            trial_environment["JAIPILOT_STATE_HOME"] = str(work_root / "state" / trial_id)
            prompt = task.prompt.read_text(encoding="utf-8")
            print(f"[{index}] run {trial_id}", flush=True)
            status, seconds, timed_out = run_agent(
                trial,
                result_directory,
                prompt,
                trial_environment,
                arguments.timeout_seconds,
                trial_plugin_root,
            )
            result = evaluate_trial(
                task,
                trial,
                result_directory,
                trial_environment,
                status,
                seconds,
                timed_out,
                arm,
                repetition,
            )
            result_path.write_text(
                json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
            )
            write_summary(work_root)
            print(
                f"[{index}] {trial_id}: accepted={result['accepted']} "
                f"agent={result['agentSeconds']}s hidden={result['hiddenTestsPassed']} "
                f"verify={result['fullVerifyPassed']}",
                flush=True,
            )
    finally:
        set_plugin(True)
        write_summary(work_root)
    return 0


if __name__ == "__main__":
    sys.exit(main())
